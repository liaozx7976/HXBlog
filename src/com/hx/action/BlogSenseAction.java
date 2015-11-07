package com.hx.action;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.hx.bean.Blog;
import com.hx.bean.ResponseMsg;
import com.hx.business.BlogManager;
import com.hx.util.Constants;
import com.hx.util.Log;
import com.hx.util.Tools;

public class BlogSenseAction extends HttpServlet {

	// ���Ͷ�/ �ȴ�����action
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setCharacterEncoding(Tools.DEFAULT_CHARSET);
		resp.setHeader("Content-Type","text/html;charset=" + Tools.DEFAULT_CHARSET);
		
		Integer blogId = null;
		ResponseMsg respMsg = new ResponseMsg();
		try {
			blogId = Integer.parseInt(req.getParameter("blogId") );
		} catch(Exception e) {
			blogId = null;
		}
		String sense = req.getParameter("sense");
		if(Tools.validateObjectBeNull(req, blogId, "blogId", respMsg)) {
			if(Tools.validateStringBeNull(req, sense, "sense", respMsg) ) {
				Blog blog = BlogManager.getBlog(blogId);
				respMsg.setOthers(sense);
				if(Tools.validateBlog(req, blog, respMsg) ) {
					Cookie sensedToBlog = Tools.getCookieByName(req.getCookies(), Tools.getSensedCookieName(blogId) );
					boolean isSensed = (sensedToBlog != null) && (! Constants.defaultCookieValue.equals(sensedToBlog.getValue()) );
					if(! isSensed) {
						respMsg.set(true, Constants.defaultResponseCode, "sense to : " + sense, Tools.getIPAddr(req) );
						if(sense.equals(Constants.senseGood) ) {
							blog.incGood();
						} else {
							blog.incNotGood();
						}
						BlogManager.addVisitSense(blog);
						resp.addCookie(new Cookie(Tools.getSensedCookieName(blog.getId() ), sense) );
					} else {
						sensedToBlog.setMaxAge(-1);
						if(sense.equals(Constants.senseGood) ) {
							if(! sensedToBlog.getValue().equals(sense) ) {
								respMsg.set(true, Constants.defaultResponseCode, "sense to : " + sense, Tools.getIPAddr(req) );
								blog.incGood();
								blog.decNotGood();
								resp.addCookie(new Cookie(Tools.getSensedCookieName(blog.getId() ), Constants.senseGood) );
							} else {
								respMsg.set(true, Constants.defaultResponseCode, "cancel sense to : " + sense, Tools.getIPAddr(req) );
								blog.decGood();
//								resp.addCookie(new Cookie(Tools.getSensedCookieName(blog.getId() ), Constants.defaultCookieValue) );
							}
						} else {
							if(! sensedToBlog.getValue().equals(sense) ) {
								respMsg.set(true, Constants.defaultResponseCode, "sense to : " + sense, Tools.getIPAddr(req) );
								blog.incNotGood();
								blog.decGood();
								resp.addCookie(new Cookie(Tools.getSensedCookieName(blog.getId() ), Constants.senseNotGood) );
							} else {
								respMsg.set(true, Constants.defaultResponseCode, "cancel sense to : " + sense, Tools.getIPAddr(req) );
								blog.decNotGood();
//								resp.addCookie(new Cookie(Tools.getSensedCookieName(blog.getId() ), Constants.defaultCookieValue) );
							}
						}
					}
				}
			}
		}
		
		PrintWriter out = resp.getWriter();
		String respInfo = respMsg.toString();
		out.write(respInfo );
		Tools.log(this, respInfo);
		out.close();
	}
	
}
