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


package org.apache.tomcat.util.modeler;


import java.io.Serializable;

import javax.management.MBeanFeatureInfo;


/**
 * 特征类
 */
public class FeatureInfo implements Serializable {
    private static final long serialVersionUID = -911529176124712296L;

    /**
     * 描述
     */
    protected String description = null;

    /**
     * 属性名
     */
    protected String name = null;


    protected MBeanFeatureInfo info = null;

    /**
     * 属性class类型
     */
    protected String type = null;


    // ------------------------------------------------------------- Properties

    /**
     * @return the human-readable description of this feature.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * 设置属性的描述
     * @param description 描述信息
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * @return the name of this feature, which must be unique among features
     *  in the same collection.
     */
    public String getName() {
        return this.name;
    }

    /**
     * 设置属性名
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 设置属性的class全类名
     * @return
     */
    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }


}
