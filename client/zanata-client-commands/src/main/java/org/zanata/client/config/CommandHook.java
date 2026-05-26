/*
 * Copyright 2013, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.client.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional element used to attach system commands to run before or after a
 * Zanata command (the "hooked command"). The hooked command is specified in the
 * "command" property.
 *
 * Each command can have any number of "before" and "after" entries.
 *
 * Commands specified in "before" will be run before the hooked command, in the
 * order that they are specified. Commands specified in "after" are similarly
 * run in order after the hooked command successfully completes. If any command
 * fails, including the hooked command, no further commands will be run.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CommandHook implements Serializable {

    private static final long serialVersionUID = 1L;

    private String command;
    private List<String> before = new ArrayList<String>();
    private List<String> after = new ArrayList<String>();

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getBefores() {
        return before;
    }

    public void setBefores(List<String> before) {
        this.before = before;
    }

    public List<String> getAfters() {
        return after;
    }

    public void setAfters(List<String> after) {
        this.after = after;
    }

    @Override
    public String toString() {
        StringBuilder sb =
                new StringBuilder("hook{ before-").append(command).append("[");
        for (String bef : before) {
            sb.append("\"").append(bef).append("\",");
        }
        sb.append("], after-").append(command).append("[");
        for (String aft : after) {
            sb.append("\"").append(aft).append("\",");
        }
        return sb.append("] }").toString();
    }
}
