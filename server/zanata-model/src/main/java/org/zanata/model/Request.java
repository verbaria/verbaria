/*
 * Copyright 2015, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.model;


import org.zanata.model.type.RequestType;
import java.util.Date;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.zanata.model.type.RequestState;
import io.leangen.graphql.annotations.GraphQLIgnore;

/**
 * Entity for general request in Zanata.
 *
 * @author Alex Eng <a href="aeng@redhat.com">aeng@redhat.com</a>
 */
@Access(AccessType.FIELD)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "UK_entityId_validTo",
        columnNames = { "entityId", "validTo" }))
@GraphQLIgnore
public class Request extends TimeEntityBase {
    private static final long serialVersionUID = -7765625863647796620L;
        @Column(nullable = true)
    private RequestState state = RequestState.NEW;
        @Column(nullable = false)
    @NotNull
    private RequestType requestType;
    @Column(nullable = true)
    @Size(max = 255)
    private String comment;
    // requesting account.
    @ManyToOne
    @JoinColumn(name = "requesterId", nullable = false)
    @NotNull
    private HAccount requester;
    // account who actioned on the request.
    @ManyToOne
    @JoinColumn(name = "actorId", nullable = true)
    private HAccount actor;

    public Request(RequestType requestType, HAccount requester, String entityId,
            Date validFrom) {
        this.requestType = requestType;
        this.requester = requester;
        setEntityId(entityId);
        setValidFrom(validFrom);
    }

    /**
     * Return new request with updated state. Expire this request (set validTo)
     *
     * @param actor
     * @param state
     * @param comment
     * @param date
     *            - set to validTo of this request, and validFrom of new request
     */
    public Request update(HAccount actor, RequestState state, String comment,
            Date date) {
        setValidTo(date);
        Request newRequest = new Request(this.requestType, this.requester,
                getEntityId(), date);
        newRequest.state = state;
        newRequest.comment = comment;
        newRequest.actor = actor;
        return newRequest;
    }

    public RequestState getState() {
        return this.state;
    }

    public RequestType getRequestType() {
        return this.requestType;
    }

    public String getComment() {
        return this.comment;
    }

    public HAccount getRequester() {
        return this.requester;
    }

    public HAccount getActor() {
        return this.actor;
    }

    @SuppressWarnings("unused")
    private void setState(final RequestState state) {
        this.state = state;
    }

    @SuppressWarnings("unused")
    private void setRequestType(final RequestType requestType) {
        this.requestType = requestType;
    }

    @SuppressWarnings("unused")
    private void setComment(final String comment) {
        this.comment = comment;
    }

    @SuppressWarnings("unused")
    private void setRequester(final HAccount requester) {
        this.requester = requester;
    }

    @SuppressWarnings("unused")
    private void setActor(final HAccount actor) {
        this.actor = actor;
    }

    public Request() {
    }
}
