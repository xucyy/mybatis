package com.xucy.mybatis.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @Author xucy
 * @Date 2020-02-07 12:07
 * @Description
 **/

public class MyProxy implements InvocationHandler {

  CommonInterface commonInterface;

  MyProxy(CommonInterface commonInterface){

    this.commonInterface=commonInterface;

  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if("say".equals(method.getName())){
      System.out.println("代理类说话了");
    }else{
      System.out.println("代理类做别的事情了");
    }
    return method.invoke(commonInterface,args);
  }
}
