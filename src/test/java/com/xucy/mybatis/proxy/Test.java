package com.xucy.mybatis.proxy;

import java.lang.reflect.Proxy;

/**
 * @Author xucy
 * @Date 2020-02-07 12:08
 * @Description
 **/

public class Test {

  @org.junit.Test
  public void test1(){
      MyProxy myProxy=new MyProxy(new Target());

      CommonInterface commonInterface=
        (CommonInterface)Proxy.newProxyInstance(CommonInterface.class.getClassLoader(),new Class[]{CommonInterface.class},myProxy);

    commonInterface.say();
  }

}
