/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
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
package org.zanata.rest.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic type to represent the status of a process.
 *
 * @author Carlos Munoz <a
 *         href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public class ProcessStatus {
    public enum ProcessStatusCode {
        /** The process has not been accepted by the server */
        NotAccepted,

        /** The process has been accepted but is not yet running */
        Waiting,

        /** The process is being executed */
        Running,

        /** The process has finished normally */
        Finished,

        /** The process has finshed with a failure */
        Failed,

        /** The process has been cancelled */
        Cancelled
    }

    private String url;

    private int percentageComplete;

    private List<String> messages;

    private ProcessStatusCode statusCode;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getPercentageComplete() {
        return percentageComplete;
    }

    public void setPercentageComplete(int percentageComplete) {
        this.percentageComplete = percentageComplete;
    }

    @JsonProperty("message")
    public List<String> getMessages() {
        if (messages == null) {
            messages = new ArrayList<String>();
        }
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public ProcessStatus addMessage(String message) {
        getMessages().add(message);
        return this;
    }

    public ProcessStatusCode getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(ProcessStatusCode statusCode) {
        this.statusCode = statusCode;
    }
}
