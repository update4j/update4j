/*
 * Copyright 2020 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.update4j;

/**
 * This exception is thrown when calling
 * {@link SingleInstanceManager#tryExecute()} or its overloads when there's
 * already a running instance on the system sharing the same lock file
 * directory.
 * 
 * @author Mordechai Meisels
 *
 */
public class SingleInstanceException extends Exception {
    private static final long serialVersionUID = -7232565848237883297L;
}
