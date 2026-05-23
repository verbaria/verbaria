package org.zanata.servlet;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Charsets;

/**
 * Set http request encoding to utf-8 if there is no encoding in the request
 *
 * Related bug: https://zanata.atlassian.net/browse/ZNTA-1323
 *
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@WebFilter(filterName = "CharacterEncodingFilter")
public class CharacterEncodingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (StringUtils.isEmpty(request.getCharacterEncoding())) {
            request.setCharacterEncoding(Charsets.UTF_8.name());
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
