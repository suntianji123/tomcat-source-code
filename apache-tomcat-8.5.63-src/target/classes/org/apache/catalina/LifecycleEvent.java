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
package org.apache.catalina;

import java.util.EventObject;

/**
 * 生命周期事件类
 */
public final class LifecycleEvent extends EventObject {

    private static final long serialVersionUID = 1L;


    /**
     * 实例化一个生命周期事件
     * @param lifecycle 事件源
     * @param type 事件类型 比如：before_init
     * @param data 事件参数
     */
    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {
        super(lifecycle);
        this.type = type;
        this.data = data;
    }


    /**
     * 事件参数对象
     */
    private final Object data;


    /**
     * 事件类型  比如：before_init
     */
    private final String type;


    /**
     * @return the event data of this event.
     */
    public Object getData() {
        return data;
    }


    /**
     * @return the Lifecycle on which this event occurred.
     */
    public Lifecycle getLifecycle() {
        return (Lifecycle) getSource();
    }


    /**
     * @return the event type of this event.
     */
    public String getType() {
        return this.type;
    }
}
