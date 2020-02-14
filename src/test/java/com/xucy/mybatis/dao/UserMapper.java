package com.xucy.mybatis.dao;


import com.xucy.mybatis.entity.User;

/**
 * @Author xucy
 * @Date 2020-01-21 12:22
 * @Description
 **/

public interface UserMapper {


    User selectUser(long id);

    int  insert();

}
