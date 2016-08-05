/**
 * file name : BlogCheckCodeFilter.java
 * created at : ����11:28:42 2016��8��5��
 * created by 970655147
 */

package com.hx.blog.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class BlogCheckCodeFilter implements Filter {

	@Override
	public void destroy() {
		
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain filterChain) throws IOException, ServletException {
//		System.out.println("before get checkCode");
		filterChain.doFilter(req, resp);
//		System.out.println("after get checkCode");
	}

	@Override
	public void init(FilterConfig config) throws ServletException {
		
	}

}
