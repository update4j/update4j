/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * The update4j framework keeps your application files synchronized and
 * dynamically launches the application. The framework gives you the ability to
 * customize almost every single process; as how it is started, what to do
 * before or after the update, decide whether to first launch then update
 * silently or the other way around and much more.
 * 
 * The main focus revolves around the {@link Configuration} class which contains almost all
 * logic to update and launch.
 * 
 * 
 */
module org.update4j {

    /*
     * Public API
     */
    exports org.update4j;
    exports org.update4j.inject;
    exports org.update4j.service;
    exports org.update4j.mapper;

    requires transitive java.xml;

    uses org.update4j.service.Delegate;
    uses org.update4j.service.UpdateHandler;
    uses org.update4j.service.Launcher;

    provides org.update4j.service.Delegate with org.update4j.service.DefaultBootstrap;
    provides org.update4j.service.UpdateHandler with org.update4j.service.DefaultUpdateHandler;
    provides org.update4j.service.Launcher with org.update4j.service.DefaultLauncher;

}