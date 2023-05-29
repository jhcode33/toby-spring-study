package com.jhcode.spring.ch3.user.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;

import com.jhcode.spring.ch3.user.domain.User;



public class UserDao {
	
	private DataSource dataSource;
	private JdbcContext jdbcContext;
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		
		this.jdbcContext = new JdbcContext();
		
		jdbcContext.setDataSource(dataSource);
	}
	
	public void setJdbcContext(JdbcContext jdbcContext) {
		this.jdbcContext = jdbcContext;
	}
	
	public void add(final User user) throws ClassNotFoundException, SQLException{
		 
		jdbcContext.workWithStatementStrategy(new StatementStrategy() {
			
				public PreparedStatement makePreparedStatement(Connection con) throws SQLException {
					String sql = "INSERT INTO users(id, name, password) values(?,?,?)";
					
					PreparedStatement pst = con.prepareStatement(sql);
					pst.setString(1, user.getId());
					pst.setString(2, user.getName());
					pst.setString(3, user.getPassword());
					
					return pst;
					
				}
			}//익명 내부 클래스의 끝);
		);//jdbcContext.workWithStatementStrategy() 메소드의 끝
	}//외부 클래스의 끝
	
	public User get(String id) throws ClassNotFoundException, SQLException {
		Connection con = dataSource.getConnection();
		
		String sql = "SELECT * FROM users WHERE id=?";
		PreparedStatement pst = con.prepareStatement(sql);
		pst.setString(1, id);
		
		ResultSet rs = pst.executeQuery();
		User user = null;
		if (rs.next()) {
			user = new User();
			user.setId(rs.getString("id"));
			user.setName(rs.getString("name"));
			user.setPassword(rs.getString("password"));
		}
		
		if(user == null) throw new EmptyResultDataAccessException(1);
		
		rs.close();
		pst.close();
		con.close();
		
		return user;
		
	}
	
	//== 테이블 정보 삭제 ==//
	public void deleteAll() throws SQLException {
		this.jdbcContext.workWithStatementStrategy(new StatementStrategy() {
			
			@Override
			public PreparedStatement makePreparedStatement(Connection con) throws SQLException {
				String sql = "DELETE FROM users";
				return con.prepareStatement(sql);
			}
		});
	}
	
	//== 테이블 정보 개수 조회 ==//
	public int getCount() throws SQLException {
		Connection con = null;
		PreparedStatement pst = null;
		ResultSet rs = null;
		
		try {
			con = dataSource.getConnection();
			
			String sql = "SELECT COUNT(*) FROM users";
			pst = con.prepareStatement(sql);
			
			rs = pst.executeQuery();
			rs.next();
			return rs.getInt(1);
			
		} catch (Exception e) {
			throw e;
			
		} finally {
			if (rs  != null) { try {rs.close();  } catch (SQLException e) {} }
			if (pst != null) { try {pst.close(); } catch (SQLException e) {} }
			if (con != null) { try {con.close(); } catch (SQLException e) {} }
		}
	}
}
