/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.runtimes.dflt.webapp;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.runtime.authentication.AuthenticationManager;
import org.apache.isis.core.webapp.content.ResourceCachingFilter;
import org.apache.isis.runtimes.dflt.runtime.system.context.IsisContext;
import org.apache.isis.runtimes.dflt.webapp.auth.AuthenticationSessionLookupStrategy;
import org.apache.isis.runtimes.dflt.webapp.auth.AuthenticationSessionLookupStrategy.Caching;
import org.apache.isis.runtimes.dflt.webapp.auth.AuthenticationSessionLookupStrategyUtils;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

public class IsisSessionFilter implements Filter {

    /**
     * Init parameter key for (typically, a logon) page to redirect to if the {@link AuthenticationSession} cannot be
     * found or is invalid.
     * 
     * <p>
     * Use this if there is only a single "special" page to forward to as the logon page.  If specified any request which
     * has not been authenticated will be redirected to this page.
     */
    public static final String LOGON_PAGE_KEY = "logonPage";

    /**
     * Init parameter key for the page to redirect to the servlet redirected to with no session throws an exception.
     * 
     * <p>
     * Use this if there are several "special" pages that constitute logon, for example a logon page and a registration page.
     * Do not specify a {@link #LOGON_PAGE_KEY}.
     */
    public static final String REDIRECT_TO_ON_NO_SESSION_EXCEPTION_KEY = "redirectToOnNoSessionException";

    /**
     * Init parameter key for whether the authentication session may be cached on the HttpSession.
     */
    public static final String CACHE_AUTH_SESSION_ON_HTTP_SESSION_KEY = "cacheAuthSessionOnHttpSession";

    /**
     * Init parameter key for which extensions should be ignored (typically, mappings for other viewers within the webapp context).
     * 
     * <p>
     * It can also be used to specify ignored static resources (though putting the {@link ResourceCachingFilter} first in the
     * <tt>web.xml</tt> accomplishes the same thing).
     * 
     * <p>
     * The value is expected as a comma separated list, for example:
     * <pre>htmlviewer</pre>
     */
    public static final String IGNORE_EXTENSIONS_KEY = "ignoreExtensions";

    private static final Function<String,Pattern> STRING_TO_PATTERN = new Function<String,Pattern>() {
        @Override
        public Pattern apply(String input) {
            return Pattern.compile(".*\\." + input);
        }
        
    };

    private AuthenticationSessionLookupStrategy authSessionLookupStrategy;
    private String logonPageIfNoSession;
    private String redirectToOnNoSessionException;
    private Caching caching;
    private Collection<Pattern> ignoreExtensions;


    // /////////////////////////////////////////////////////////////////
    // init, destroy
    // /////////////////////////////////////////////////////////////////

    @Override
    public void init(final FilterConfig config) throws ServletException {
        authSessionLookupStrategy = AuthenticationSessionLookupStrategyUtils.lookup(config);
        lookupLogonPageIfNoSessionKey(config);
        lookupRedirectToOnNoSessionExceptionKey(config);
        lookupCacheAuthSessionKey(config);
        lookupIgnorePatterns(config);
    }

    private void lookupLogonPageIfNoSessionKey(final FilterConfig config) {
        logonPageIfNoSession = config.getInitParameter(LOGON_PAGE_KEY);
    }

    private void lookupRedirectToOnNoSessionExceptionKey(final FilterConfig config) {
        redirectToOnNoSessionException = config.getInitParameter(REDIRECT_TO_ON_NO_SESSION_EXCEPTION_KEY);
    }

    private void lookupCacheAuthSessionKey(final FilterConfig config) {
        caching = Caching.lookup(config.getInitParameter(CACHE_AUTH_SESSION_ON_HTTP_SESSION_KEY));
    }

    private void lookupIgnorePatterns(final FilterConfig config) {
        ignoreExtensions = Collections.unmodifiableCollection(parseIgnorePatterns(config));
    }

    private Collection<Pattern> parseIgnorePatterns(final FilterConfig config) {
        final String ignoreExtensionsStr = config.getInitParameter(IGNORE_EXTENSIONS_KEY);
        if(ignoreExtensionsStr != null) {
            final List<String> ignoreExtensions = Lists.newArrayList(Splitter.on(",").split(ignoreExtensionsStr));
            return Collections2.transform(ignoreExtensions, STRING_TO_PATTERN);
        }
        return Lists.newArrayList();
    }


    @Override
    public void destroy() {
    }

    // /////////////////////////////////////////////////////////////////
    // doFilter
    // /////////////////////////////////////////////////////////////////

    public enum SessionState {
        
        UNDEFINED {
            @Override
            public void handle(IsisSessionFilter filter, ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                
                final HttpServletRequest httpRequest = (HttpServletRequest) request;
                final HttpServletResponse httpResponse = (HttpServletResponse) response;

                if(requestIsIgnoreExtension(filter, httpRequest)) {
                    try {
                        chain.doFilter(request, response);
                        return;
                    } finally {
                        closeSession();
                    }
                }

                if(ResourceCachingFilter.isCachedResource(httpRequest)) {
                    try {
                        chain.doFilter(request, response);
                        return;
                    } finally {
                        closeSession();
                    }
                }
                
                // request is to logon page
                if (requestToLogonPage(filter, httpRequest)) {
                    NO_SESSION_SINCE_REDIRECTING_TO_LOGON_PAGE.setOn(request);
                    try {
                        chain.doFilter(request, response);
                        return;
                    } finally {
                        UNDEFINED.setOn(request);
                        closeSession();
                    }
                }

                // authenticate
                final AuthenticationSession validSession = filter.authSessionLookupStrategy.lookupValid(request, response, filter.caching);
                if (validSession != null) {
                    filter.authSessionLookupStrategy.bind(request, response, validSession, filter.caching);

                    openSession(validSession);
                    SESSION_IN_PROGRESS.setOn(request);
                    try {
                        chain.doFilter(request, response);
                    } finally {
                        UNDEFINED.setOn(request);
                        closeSession();
                    }
                    return;
                }
                
                // redirect to logon page (if there is one)
                if (filter.logonPageIfNoSession != null) {
                    // no need to set state, since not proceeding along the filter chain
                    httpResponse.sendRedirect(filter.logonPageIfNoSession);
                    return;
                }
                
                // the destination servlet is expected to know that there
                // will be no open context
                // if it does not, then we will redirect to logon page
                try {
                    NO_SESSION_SINCE_NOT_AUTHENTICATED.setOn(request);
                    chain.doFilter(request, response);
                } catch(RuntimeException ex) {
                    // in case the destination servlet cannot cope, but we've been told
                    // to redirect elsewhere
                    if(filter.redirectToOnNoSessionException != null) {
                        httpResponse.sendRedirect(filter.redirectToOnNoSessionException);
                        return;
                    }
                    throw ex;
                } catch(IOException ex) {
                    if(filter.redirectToOnNoSessionException != null) {
                        httpResponse.sendRedirect(filter.redirectToOnNoSessionException);
                        return;
                    }
                    throw ex;
                } catch(ServletException ex) {
                    // in case the destination servlet cannot cope, but we've been told
                    // to redirect elsewhere
                    if(filter.redirectToOnNoSessionException != null) {
                        httpResponse.sendRedirect(filter.redirectToOnNoSessionException);
                        return;
                    }
                    throw ex;
                } finally {
                    UNDEFINED.setOn(request);
                    // nothing to do
                }
            }

            private boolean requestIsIgnoreExtension(IsisSessionFilter filter, HttpServletRequest httpRequest) {
                final String servletPath = httpRequest.getServletPath();
                for (final Pattern extension : filter.ignoreExtensions) {
                    if(extension.matcher(servletPath).matches()) {
                        return true;
                    }
                }
                return false;
            }

            private boolean requestToLogonPage(IsisSessionFilter filter, HttpServletRequest httpRequest) {
                return filter.logonPageIfNoSession != null &&
                       filter.logonPageIfNoSession.equals(httpRequest.getServletPath());
            }
        },
        NO_SESSION_SINCE_REDIRECTING_TO_LOGON_PAGE,
        NO_SESSION_SINCE_NOT_AUTHENTICATED,
        SESSION_IN_PROGRESS;

        static SessionState lookup(ServletRequest request) {
            final Object state = request.getAttribute(SESSION_STATE_KEY);
            return state != null ? (SessionState)state : SessionState.UNDEFINED;
        }


        boolean isValid(final AuthenticationSession authSession) {
            return authSession != null && getAuthenticationManager().isSessionValid(authSession);
        }

        void setOn(ServletRequest request) {
            request.setAttribute(SESSION_STATE_KEY, this);
        }

        public void handle(IsisSessionFilter filter, ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    
        AuthenticationManager getAuthenticationManager() {
            return IsisContext.getAuthenticationManager();
        }

        void openSession(final AuthenticationSession authSession) {
            IsisContext.openSession(authSession);
        }

        void closeSession() {
            IsisContext.closeSession();
        }
    
    }
    
    static final String SESSION_STATE_KEY = IsisSessionFilter.SessionState.class.getName();

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
        throws IOException, ServletException {
        
        SessionState sessionState = SessionState.lookup(request);
        sessionState.handle(this, request, response, chain);
    }
    

}