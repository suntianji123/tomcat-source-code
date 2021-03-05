/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import java.lang.reflect.Method;

import org.apache.catalina.Executor;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;


/**
 * Rule implementation that creates a connector.
 */

public class ConnectorCreateRule extends Rule {

    private static final Log log = LogFactory.getLog(ConnectorCreateRule.class);
    protected static final StringManager sm = StringManager.getManager(ConnectorCreateRule.class);
    // --------------------------------------------------------- Public Methods


    /**
     * 开始解析Connector标签
     * @param namespace 命名空间
     * @param name 标签名
     * @param attributes 变迁属性列表
     *
     * @throws Exception
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        //获取父标签的StandardService对象
        Service svc = (Service)digester.peek();

        //定义执行器
        Executor ex = null;
        if ( attributes.getValue("executor")!=null ) {//如果Connector标签中设置了executor属性
            //从属性executor中获取值  并且根据属性值 获取执行器
            ex = svc.getExecutor(attributes.getValue("executor"));
        }

        //获取Connector标签中的protocol协议属性 获取用户指定的协议类型HTTP/1.1 创建一个连接器
        Connector con = new Connector(attributes.getValue("protocol"));
        if (ex != null) {//如果指定了执行器
            //设置连接器的协议处理器的执行器
            setExecutor(con, ex);
        }

        //连接器的ssl证书属性名的属性值
        String sslImplementationName = attributes.getValue("sslImplementationName");
        if (sslImplementationName != null) {//存在ssl证书相关
            //设置连接器的处理器的ssl证书实现器名称
            setSSLImplementationName(con, sslImplementationName);
        }

        //将当前连接器对象放入 标签对象数据栈
        digester.push(con);
    }

    /**
     * 设置连接器的协议处理器的执行器
     * @param con 连接器
     * @param ex 主席星期
     * @throws Exception
     */
    private static void setExecutor(Connector con, Executor ex) throws Exception {
        //获取连接器的协议处理器的setExecutor方法
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(),"setExecutor",new Class[] {java.util.concurrent.Executor.class});
        if (m!=null) {//存在setExecutor方法
            //执行方法 设置协议处理器的执行器
            m.invoke(con.getProtocolHandler(), new Object[] {ex});
        }else {
            log.warn(sm.getString("connector.noSetExecutor", con));
        }
    }

    /**
     * 设置连接器的协议处理器的ssl证书实现名称
     * @param con 连接器
     * @param sslImplementationName ssl证书实现名称
     * @throws Exception
     */
    private static void setSSLImplementationName(Connector con, String sslImplementationName) throws Exception {
        //查找连接器的协议处理器的setSslImplementationName方法
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(),"setSslImplementationName",new Class[] {String.class});
        if (m != null) {//存在方法
            //执行器连接器的协议处理器的setSslImplementationName方法
            m.invoke(con.getProtocolHandler(), new Object[] {sslImplementationName});
        } else {
            log.warn(sm.getString("connector.noSetSSLImplementationName", con));
        }
    }

    /**
     * Process the end of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     */
    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
    }


}
