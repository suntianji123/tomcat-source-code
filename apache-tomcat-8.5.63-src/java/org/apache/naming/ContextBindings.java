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

import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Handles the associations :
 * <ul>
 * <li>Object with a NamingContext</li>
 * <li>Calling thread with a NamingContext</li>
 * <li>Calling thread with object bound to the same naming context</li>
 * <li>Thread context class loader with a NamingContext</li>
 * <li>Thread context class loader with object bound to the same
 *     NamingContext</li>
 * </ul>
 * The objects are typically Catalina Server or Context objects.
 *
 * @author Remy Maucherat
 */
public class ContextBindings {

    // -------------------------------------------------------------- Variables

    /**
     * 将对象与nameingContext对象 绑定 比如StandardServer | nameContext{name="/",{}}
     */
    private static final Hashtable<Object,Context> objectBindings = new Hashtable<>();


    /**
     * 线程的上下文对象
     */
    private static final Hashtable<Thread,Context> threadBindings = new Hashtable<>();


    /**
     * Bindings thread - object. Keyed by thread.
     */
    private static final Hashtable<Thread,Object> threadObjectBindings = new Hashtable<>();


    /**
     * 类加载器 | 上下文对象  比如 URLClassLoader | NamingContext
     */
    private static final Hashtable<ClassLoader,Context> clBindings = new Hashtable<>();


    /**
     * 类加载器 | 对象  比如URLClassLoader | StandardServer
     */
    private static final Hashtable<ClassLoader,Object> clObjectBindings = new Hashtable<>();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ContextBindings.class);


    // --------------------------------------------------------- Public Methods

    /**
     * Binds an object and a naming context.
     *
     * @param obj       Object to bind with naming context
     * @param context   Associated naming context instance
     */
    public static void bindContext(Object obj, Context context) {
        bindContext(obj, context, null);
    }


    /**
     * 将对象与命名上下文对象绑定
     * @param obj 比如StandardServer
     * @param context 比如nameingContext对象
     * @param token 令牌对象
     */
    public static void bindContext(Object obj, Context context, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            objectBindings.put(obj, context);
        }
    }


    /**
     * Unbinds an object and a naming context.
     *
     * @param obj   Object to unbind
     * @param token Security token
     */
    public static void unbindContext(Object obj, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            objectBindings.remove(obj);
        }
    }


    /**
     * Retrieve a naming context.
     *
     * @param obj   Object bound to the required naming context
     */
    static Context getContext(Object obj) {
        return objectBindings.get(obj);
    }


    /**
     * Binds a naming context to a thread.
     *
     * @param obj   Object bound to the required naming context
     * @param token Security token
     *
     * @throws NamingException If no naming context is bound to the provided
     *         object
     */
    public static void bindThread(Object obj, Object token) throws NamingException {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Context context = objectBindings.get(obj);
            if (context == null) {
                throw new NamingException(
                        sm.getString("contextBindings.unknownContext", obj));
            }
            threadBindings.put(Thread.currentThread(), context);
            threadObjectBindings.put(Thread.currentThread(), obj);
        }
    }


    /**
     * Unbinds a thread and a naming context.
     *
     * @param obj   Object bound to the required naming context
     * @param token Security token
     */
    public static void unbindThread(Object obj, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            threadBindings.remove(Thread.currentThread());
            threadObjectBindings.remove(Thread.currentThread());
        }
    }


    /**
     * Retrieves the naming context bound to the current thread.
     *
     * @return The naming context bound to the current thread.
     *
     * @throws NamingException If no naming context is bound to the current
     *         thread
     */
    public static Context getThread() throws NamingException {
        Context context = threadBindings.get(Thread.currentThread());
        if (context == null) {
            throw new NamingException
                    (sm.getString("contextBindings.noContextBoundToThread"));
        }
        return context;
    }


    /**
     * Retrieves the name of the object bound to the naming context that is also
     * bound to the current thread.
     */
    static String getThreadName() throws NamingException {
        Object obj = threadObjectBindings.get(Thread.currentThread());
        if (obj == null) {
            throw new NamingException
                    (sm.getString("contextBindings.noContextBoundToThread"));
        }
        return obj.toString();
    }


    /**
     * Tests if current thread is bound to a naming context.
     *
     * @return <code>true</code> if the current thread is bound to a naming
     *         context, otherwise <code>false</code>
     */
    public static boolean isThreadBound() {
        return threadBindings.containsKey(Thread.currentThread());
    }


    /**
     * 给类加载器指定上下文
     * @param obj 对象
     * @param token 访问对象的令牌对象
     * @param classLoader 类加载器对象
     * @throws NamingException
     */
    public static void bindClassLoader(Object obj, Object token,
            ClassLoader classLoader) throws NamingException {
        if (ContextAccessController.checkSecurityToken(obj, token)) {//判断对象是否注册的这个令牌对象
            //获取对象的上下文对象
            Context context = objectBindings.get(obj);
            if (context == null) {
                throw new NamingException
                        (sm.getString("contextBindings.unknownContext", obj));
            }

            //设置类加载器的上下文对象
            clBindings.put(classLoader, context);
            //设置类加载器加载对象
            clObjectBindings.put(classLoader, obj);
        }
    }


    /**
     * Unbinds a naming context and a class loader.
     *
     * @param obj           Object bound to the required naming context
     * @param token         Security token
     * @param classLoader   The class loader bound to the naming context
     */
    public static void unbindClassLoader(Object obj, Object token,
            ClassLoader classLoader) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Object o = clObjectBindings.get(classLoader);
            if (o == null || !o.equals(obj)) {
                return;
            }
            clBindings.remove(classLoader);
            clObjectBindings.remove(classLoader);
        }
    }


    /**
     * 获取类加载器的NamingContext对象
     * @return
     * @throws NamingException
     */
    public static Context getClassLoader() throws NamingException {
        //获取当前线程的类加载器
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        //结果Context对象
        Context context = null;
        do {
            //根据类加载器获取Context对象   NamingContext对象
            context = clBindings.get(cl);
            if (context != null) {
                return context;
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException(sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * Retrieves the name of the object bound to the naming context that is also
     * bound to the thread context class loader.
     */
    static String getClassLoaderName() throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object obj = null;
        do {
            obj = clObjectBindings.get(cl);
            if (obj != null) {
                return obj.toString();
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException (sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * Tests if the thread context class loader is bound to a context.
     *
     * @return <code>true</code> if the thread context class loader or one of
     *         its parents is bound to a naming context, otherwise
     *         <code>false</code>
     */
    public static boolean isClassLoaderBound() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        do {
            if (clBindings.containsKey(cl)) {
                return true;
            }
        } while ((cl = cl.getParent()) != null);
        return false;
    }
}
