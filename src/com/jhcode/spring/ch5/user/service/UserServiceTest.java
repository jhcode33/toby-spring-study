package com.jhcode.spring.ch5.user.service;

import static com.jhcode.spring.ch5.user.service.UserLevelUpgradeImpl.MIN_LOGCOUNT_FOR_SILVER;
import static com.jhcode.spring.ch5.user.service.UserLevelUpgradeImpl.MIN_RECOMMEND_FOR_GOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.jhcode.spring.ch5.user.dao.UserDao;
import com.jhcode.spring.ch5.user.domain.Level;
import com.jhcode.spring.ch5.user.domain.User;
import com.jhcode.spring.ch5.user.service.TestUserService.TestUserServiceException;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestServiceFactory.class})
public class UserServiceTest {
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private PlatformTransactionManager transactionManager;
	
	@Autowired
	private UserDao userDao;
	
	List<User> users;
	
	@BeforeEach
	public void setUp() {
		
		//배열을 리스트로 만들어주는 메소드
		users = Arrays.asList(
				new User("user1", "user1", "p1", Level.BASIC, MIN_LOGCOUNT_FOR_SILVER -1, 0, "user1@go.kr"),
				new User("user2", "user2", "p2", Level.BASIC, MIN_LOGCOUNT_FOR_SILVER, 0, "user2@go.kr"),
				new User("user3", "user3", "p3", Level.SILVER, 60, MIN_RECOMMEND_FOR_GOLD -1, "user3@go.kr"),
				new User("user4", "user4", "p4", Level.SILVER, 60, MIN_RECOMMEND_FOR_GOLD, "user4@go.kr"),
				new User("user5", "user5", "p5", Level.GOLD, 100, 100, "user5@go.kr")
				);
	}
	
	//User의 Level과 인수로 받은 Level의 값을 비교하는 메소드
	//어떤 레벨로 바뀌는지가 아니라, 다음 레벨로 바뀔 것인지를 확인한다.
	private void checkLevel(User user, boolean upgraded) {
		Optional<User> optionalUser = userDao.get(user.getId());
		
		if(optionalUser != null) {
			User userUpdate = optionalUser.get();
			
			if(upgraded) {
				System.out.println(user.getId() + " : Level 업그레이드 되었음");
				assertEquals(userUpdate.getLevel(), user.getLevel().nextLevel());
			
			} else {
				System.out.println(user.getId() + " : Level 업그레이드 되지 않음");
				assertEquals(userUpdate.getLevel(), user.getLevel());
			}
			
		}
	}
	
	//== upgradeLevels() 테스트에 사용될 Mock 객체 ==//
	static class MockMailSender implements MailSender {
		private List<String> requests = new ArrayList<String>();	
		
		public List<String> getRequests() {
			return requests;
		}

		public void send(SimpleMailMessage mailMessage) throws MailException {
			requests.add(mailMessage.getTo()[0]);  
		}

		public void send(SimpleMailMessage[] mailMessage) throws MailException {
		}
	}
	
	@Test @DirtiesContext
	public void upgradeLevels() throws Exception {
		userDao.deleteAll();
		
		for(User user : users) {
			userDao.add(user);
		}
		
		MockMailSender mockMailSender = new MockMailSender();
		UserLevelUpgradeImpl policy = new UserLevelUpgradeImpl();
		policy.setMailSender(mockMailSender);
		userService.setUserLevelUpgradePolicy(policy);
		
		//DB의 모든 User을 가지고와서 Level 등급을 조정함
		userService.upgradeLevels();
		
		checkLevel(users.get(0), false);
		checkLevel(users.get(1), true);
		checkLevel(users.get(2), false);
		checkLevel(users.get(3), true);
		checkLevel(users.get(4), false);
		
		List<String> request = mockMailSender.getRequests();
		assertEquals(request.size(), 2);
		assertEquals(request.get(0), users.get(1).getEmail());
		assertEquals(request.get(1), users.get(3).getEmail());
	}
	
	@Test
	public void add() {
		userDao.deleteAll();
		
		User userWithLevel = users.get(4); //GOLD
		User userWithOutLevel = users.get(0); //BASIC
		userWithOutLevel.setLevel(null); //BASIC -> NULL, 비어있으면 다시 BASIC으로 설정되어야함.
		
		//GOLD -> GOLD 그대로 유지
		userService.add(userWithLevel);
		
		//Null -> BASIC 처음 가입 유저는 BASIC으로 설정
		userService.add(userWithOutLevel);
		
		//DB에 저장된 것을 불러와서 저장한 값이랑 비교함.
		Optional<User> optionalLevelUser = userDao.get(userWithLevel.getId());
		if(optionalLevelUser != null) {
			User userWithLevelRead = optionalLevelUser.get();
			assertEquals(userWithLevelRead.getLevel(), userWithLevel.getLevel());
		}
		
		Optional<User> optionalOutLevelUser = userDao.get(userWithOutLevel.getId());
		if(optionalOutLevelUser != null) { 
			User userWithOutLevelRead = optionalOutLevelUser.get();
			assertEquals(userWithOutLevelRead.getLevel(), userWithOutLevel.getLevel());
		}
	}
	
	//예외 발생 시 작업 취소 여부 테스트
	@Test
	public void upgradeAllorNothing() throws Exception {
		UserService testUserService = new TestUserService(users.get(3).getId());
		UserLevelUpgradeImpl policy = new UserLevelUpgradeImpl();
		policy.setMailSender(new DummyMailSender());
		
		testUserService.setUserDao(userDao);
		testUserService.setTranscationManager(transactionManager);
		testUserService.setUserLevelUpgradePolicy(policy);
		
		userDao.deleteAll();
		
		for (User user : users) {
			testUserService.add(user);
		}
		
		try {
			testUserService.upgradeLevels();
			//테스트가 제대로 동작하게 하기 위한 안전장치, 로직을 잘못짜서 upgradeLevels() 메소드가 통과되도 무조건 실패함.
			//fail("TestUserServiceException expected");
		} catch (TestUserServiceException e) {
			System.out.println("TestUserServiceException 예외 발생함");
		} finally {
			checkLevel(users.get(1), false);
		}
	}
}
