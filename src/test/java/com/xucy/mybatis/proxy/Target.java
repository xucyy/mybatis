package com.xucy.mybatis.proxy;

/**
 * @Author xucy
 * @Date 2020-02-07 12:05
 * @Description
 **/

public class Target implements CommonInterface{

  @Override
  public void say() {
    System.out.println("目标类说话了！！！");
  }

}
