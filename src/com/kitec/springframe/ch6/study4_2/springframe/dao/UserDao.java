package com.kitec.springframe.ch6.study4_2.springframe.dao;

import java.util.List;
import java.util.Optional;

import com.kitec.springframe.ch6.study4_2.springframe.domain.User;

public interface UserDao {
	
	void add(User user);

	Optional<User> get(String id);

	List<User> getAll();

	void deleteAll();

	int getCount();
	
	public void update(User user);
}
