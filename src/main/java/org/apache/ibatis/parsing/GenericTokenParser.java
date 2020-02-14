/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 * todo 是一个通用的字占位符解析器
 */
public class GenericTokenParser {

  //todo 占位符的开始标记
  private final String openToken;
  //todo 占位符的结束标记
  private final String closeToken;
  //todo TokenHandler接口的实现，会按照一定的逻辑解析占位符
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }
  //todo 它会顺序查找 openToken 和closeToken,解析得到占位符的字面值，将其交给TokenHandler处理，
  //  然后将解析结果重新拼装成字符串并返回
  public String parse(String text) {
    //todo 检测text是否为空
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token todo 查找开始标记
    int start = text.indexOf(openToken);
    //todo 检测开始标记是否为-1
    if (start == -1) {
      return text;
    }
    //todo
    char[] src = text.toCharArray();
    int offset = 0;
    //todo 用来记录解析后的字符串
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    while (start > -1) {
      if (start > 0 && src[start - 1] == '\\') {
        // todo 遇到转义的开始标记，则直接将前面的字符串以及开始标记追加到builder中
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // todo 查找到开始标记，且未转义
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        //todo 将前面的字符串追加到 builder中
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            //todo 处理转义的结束标记
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            //todo 将开始标记和结束标记之间的字符串追加到expression中保存
            expression.append(src, offset, end - offset);
            break;
          }
        }
        if (end == -1) {
          // todo 未找到结束标记
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          //todo 将占位符的字面值交给TokenHandler处理，并将处理结果追加到builder中
          // 最终拼凑出解析后的完整内容
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      //todo 移动start
      start = text.indexOf(openToken, offset);
    }
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
