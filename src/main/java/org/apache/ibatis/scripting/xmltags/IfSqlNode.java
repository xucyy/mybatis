/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

/**
 * @author Clinton Begin
 * todo 对应的动态SQL节点是<if> 节点
 */
public class IfSqlNode implements SqlNode {
  //todo 用于解析<if>节点的test表达式的值
  private final ExpressionEvaluator evaluator;
  //todo 记录了<if> 节点中的test表达式
  private final String test;
  //todo 记录了<if>节点的子节点
  private final SqlNode contents;

  public IfSqlNode(SqlNode contents, String test) {
    this.test = test;
    this.contents = contents;
    this.evaluator = new ExpressionEvaluator();
  }

  @Override
  public boolean apply(DynamicContext context) {
    //todo 通过 evaluateBoolean方法 来检测其test表达式是否为true，然后根据test表达式的结果，决定是否执行其子节点的apply()方法
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      //todo test表达式为true,则执行子节点的apply()方法
      contents.apply(context);
      return true;
    }
    //todo 返回值代表的是，test表达式是否为true
    return false;
  }

}
