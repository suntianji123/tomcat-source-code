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
package org.apache.naming;

import java.util.Hashtable;

/**
 * 上下访问控制器
 *
 * @author Remy Maucherat
 */
public class ContextAccessController {

    // -------------------------------------------------------------- Variables

    /**
     * 只读的Context对象列表
     */
    private static final Hashtable<Object,Object> readOnlyContexts = new Hashtable<>();


    /**
     * 访问某个资源目录与安全令牌的映射表
     */
    private static final Hashtable<Object,Object> securityTokens = new Hashtable<>();


    // --------------------------------------------------------- Public Methods

    /**
     * 设置反问某个命名资源目录的安全令牌
     * @param name 资源目录
     * @param token 令牌对象
     */
    public static void setSecurityToken(Object name, Object token) {
        //获取系统安全管理器
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(
                    ContextAccessController.class.getName()
                            + ".setSecurityToken"));
        }

        //令牌表中没有设置过这个令牌
        if ((!securityTokens.containsKey(name)) && (token != null)) {
            //将目录对应的秘钥对象 返回映射表中
            securityTokens.put(name, token);
        }
    }


    /**
     * Remove a security token for a context.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void unsetSecurityToken(Object name, Object token) {
        if (checkSecurityToken(name, token)) {
            securityTokens.remove(name);
        }
    }


    /**
     * 检查安全令牌表中是否存在 指定对象的令牌对象
     * @param name 指定对象 比如StandardServer
     * @param token 秘钥对象
     * @return
     */
    public static boolean checkSecurityToken
        (Object name, Object token) {
        //根据对象获取秘钥对象
        Object refToken = securityTokens.get(name);
        //秘钥对象存在 并且不能为null
        return (refToken == null || refToken.equals(token));
    }


    /**
     * 移除只读的秘钥对象
     * @param name 名称
     * @param token 秘钥对象
     */
    public static void setWritable(Object name, Object token) {
        if (checkSecurityToken(name, token))
            readOnlyContexts.remove(name);
    }


    /**
     * Set whether or not a Catalina context is writable.
     *
     * @param name Name of the Catalina context
     */
    public static void setReadOnly(Object name) {
        readOnlyContexts.put(name, name);
    }


    /**
     * 判断Context对象 是否为只读 不能向其中绑定对象
     * @param name 上下文名称
     * @return
     */
    public static boolean isWritable(Object name) {
        return !(readOnlyContexts.containsKey(name));
    }
}

