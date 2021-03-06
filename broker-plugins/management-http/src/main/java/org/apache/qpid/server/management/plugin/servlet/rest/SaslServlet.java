/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.management.plugin.HttpManagementConfiguration;
import org.apache.qpid.server.management.plugin.HttpManagementUtil;
import org.apache.qpid.server.management.plugin.servlet.ServletConnectionPrincipal;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;

public class SaslServlet extends AbstractServlet
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SaslServlet.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ATTR_RANDOM = "SaslServlet.Random";
    private static final String ATTR_ID = "SaslServlet.ID";
    private static final String ATTR_SASL_SERVER = "SaslServlet.SaslServer";
    private static final String ATTR_EXPIRY = "SaslServlet.Expiry";
    private static final long SASL_EXCHANGE_EXPIRY = 3000L;

    public SaslServlet()
    {
        super();
    }

    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws
                                                                                   ServletException,
                                                                                   IOException
    {
        HttpSession session = request.getSession();
        getRandom(session);

        SubjectCreator subjectCreator = getSubjectCreator(request);
        List<String> mechanismsList = subjectCreator.getMechanisms();
        String[] mechanisms = mechanismsList.toArray(new String[mechanismsList.size()]);
        Map<String, Object> outputObject = new LinkedHashMap<String, Object>();

        final Subject subject = getAuthorisedSubject(request);
        if(subject != null)
        {
            Principal principal = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
            outputObject.put("user", principal.getName());
        }
        else if (request.getRemoteUser() != null)
        {
            outputObject.put("user", request.getRemoteUser());
        }

        outputObject.put("mechanisms", (Object) mechanisms);

        sendJsonResponse(outputObject, request, response);

    }

    private Random getRandom(final HttpSession session)
    {
        Random rand = (Random) session.getAttribute(ATTR_RANDOM);
        if(rand == null)
        {
            synchronized (SECURE_RANDOM)
            {
                rand = new Random(SECURE_RANDOM.nextLong());
            }
            session.setAttribute(ATTR_RANDOM, rand);
        }
        return rand;
    }


    @Override
    protected void doPostWithSubjectAndActor(final HttpServletRequest request, final HttpServletResponse response) throws IOException
    {
        checkSaslAuthEnabled(request);

        try
        {
            HttpSession session = request.getSession();

            String mechanism = request.getParameter("mechanism");
            String id = request.getParameter("id");
            String saslResponse = request.getParameter("response");

            SubjectCreator subjectCreator = getSubjectCreator(request);

            if(mechanism != null)
            {
                if(id == null)
                {
                    LOGGER.debug("Creating SaslServer for mechanism: {}", mechanism);

                    SaslServer saslServer = subjectCreator.createSaslServer(mechanism, request.getServerName(), null/*TODO*/);
                    evaluateSaslResponse(request, response, session, saslResponse, saslServer, subjectCreator);
                }
                else
                {
                    response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                    session.removeAttribute(ATTR_ID);
                    session.removeAttribute(ATTR_SASL_SERVER);
                    session.removeAttribute(ATTR_EXPIRY);
                }
            }
            else
            {
                if(id != null)
                {
                    if(id.equals(session.getAttribute(ATTR_ID)) && System.currentTimeMillis() < (Long) session.getAttribute(ATTR_EXPIRY))
                    {
                        SaslServer saslServer = (SaslServer) session.getAttribute(ATTR_SASL_SERVER);
                        evaluateSaslResponse(request, response, session, saslResponse, saslServer, subjectCreator);
                    }
                    else
                    {
                        response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                        session.removeAttribute(ATTR_ID);
                        session.removeAttribute(ATTR_SASL_SERVER);
                        session.removeAttribute(ATTR_EXPIRY);
                    }
                }
                else
                {
                    response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                    session.removeAttribute(ATTR_ID);
                    session.removeAttribute(ATTR_SASL_SERVER);
                    session.removeAttribute(ATTR_EXPIRY);
                }
            }
        }
        catch(IOException e)
        {
            LOGGER.error("Error processing SASL request", e);
            throw e;
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Error processing SASL request", e);
            throw e;
        }
    }

    private void checkSaslAuthEnabled(HttpServletRequest request)
    {
        boolean saslAuthEnabled = false;
        HttpManagementConfiguration management = getManagementConfiguration();
        if (request.isSecure())
        {
            saslAuthEnabled = management.isHttpsSaslAuthenticationEnabled();
        }
        else
        {
            saslAuthEnabled = management.isHttpSaslAuthenticationEnabled();
        }
        if (!saslAuthEnabled)
        {
            throw new ConnectionScopedRuntimeException("Sasl authentication disabled.");
        }
    }

    private void evaluateSaslResponse(final HttpServletRequest request,
                                      final HttpServletResponse response,
                                      final HttpSession session, final String saslResponse, final SaslServer saslServer, SubjectCreator subjectCreator) throws IOException
    {
        final String id;
        byte[] challenge;
        try
        {
            challenge  = saslServer.evaluateResponse(saslResponse == null
                                                             ? new byte[0]
                                                             : DatatypeConverter.parseBase64Binary(saslResponse));
        }
        catch(SaslException e)
        {
            session.removeAttribute(ATTR_ID);
            session.removeAttribute(ATTR_SASL_SERVER);
            session.removeAttribute(ATTR_EXPIRY);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            return;
        }

        if(saslServer.isComplete())
        {
            Subject originalSubject = subjectCreator.createSubjectWithGroups(new AuthenticatedPrincipal(saslServer.getAuthorizationID()));
            Subject subject = new Subject(false,
                                          originalSubject.getPrincipals(),
                                          originalSubject.getPublicCredentials(),
                                          originalSubject.getPrivateCredentials());
            subject.getPrincipals().add(new ServletConnectionPrincipal(request));
            subject.setReadOnly();

            Broker broker = getBroker();
            try
            {
                HttpManagementUtil.assertManagementAccess(broker.getSecurityManager(), subject);
            }
            catch(SecurityException e)
            {
                sendError(response, HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            HttpManagementUtil.saveAuthorisedSubject(request.getSession(), subject);
            session.removeAttribute(ATTR_ID);
            session.removeAttribute(ATTR_SASL_SERVER);
            session.removeAttribute(ATTR_EXPIRY);
            if(challenge != null && challenge.length != 0)
            {
                Map<String, Object> outputObject = new LinkedHashMap<String, Object>();
                outputObject.put("challenge", DatatypeConverter.printBase64Binary(challenge));

                sendJsonResponse(outputObject, request, response);
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }
        else
        {
            Random rand = getRandom(session);
            id = String.valueOf(rand.nextLong());
            session.setAttribute(ATTR_ID, id);
            session.setAttribute(ATTR_SASL_SERVER, saslServer);
            session.setAttribute(ATTR_EXPIRY, System.currentTimeMillis() + SASL_EXCHANGE_EXPIRY);

            response.setStatus(HttpServletResponse.SC_OK);

            Map<String, Object> outputObject = new LinkedHashMap<String, Object>();
            outputObject.put("id", id);
            outputObject.put("challenge", DatatypeConverter.printBase64Binary(challenge));

            sendJsonResponse(outputObject, request, response);
        }
    }

    private SubjectCreator getSubjectCreator(HttpServletRequest request)
    {
        return HttpManagementUtil.getManagementConfiguration(getServletContext()).getAuthenticationProvider(request).getSubjectCreator(
                request.isSecure());
    }

    @Override
    protected Subject getAuthorisedSubject(HttpServletRequest request)
    {
        Subject subject = HttpManagementUtil.getAuthorisedSubject(request.getSession());
        if(subject == null)
        {
            subject = HttpManagementUtil.tryToAuthenticate(request, HttpManagementUtil.getManagementConfiguration(getServletContext()));
        }
        return subject;
    }

}
