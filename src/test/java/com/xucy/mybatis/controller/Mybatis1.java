package com.xucy.mybatis.controller;

import com.xucy.mybatis.dao.UserMapper;
import com.xucy.mybatis.entity.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author xucy
 * @Date 2020-01-21 11:51
 * @Description
 **/

public class Mybatis1 {

  @Test
  public void test1(){
    System.out.println("111");
  }

  @Test
  public void test2() throws IOException {
    String resource = "com/xucy/mybatis/config/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory =
      new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession = sqlSessionFactory.openSession();
    User user = sqlSession.selectOne("com.xucy.mybatis.dao.UserMapper.selectUser", 1);

    System.out.println(user);
  }

  @Test
  public void test3() throws IOException {
    String resource = "com/xucy/mybatis/config/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory =
      new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession = sqlSessionFactory.openSession();
    int i = sqlSession.insert("com.xucy.mybatis.dao.UserMapper.insert");

    System.out.println(i);
  }

  @Test
  public void test4() throws IOException {
    String resource = "com/xucy/mybatis/config/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory =
      new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession = sqlSessionFactory.openSession();
    UserMapper userMapper=sqlSession.getMapper(UserMapper.class);
    int i=userMapper.insert();

    System.out.println(i);
  }
}
