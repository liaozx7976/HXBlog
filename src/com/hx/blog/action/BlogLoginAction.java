package com.hx.blog.action;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.sf.json.JSONObject;

import com.hx.blog.bean.UserInfo;
import com.hx.blog.interf.BaseAction;
import com.hx.blog.util.Constants;
import com.hx.blog.util.Log;
import com.hx.blog.util.Tools;

// 获取blog配置的action
public class BlogLoginAction extends BaseAction {

	// 登录校验
	// 获取用户名密码
	// 校验用户名, 密码
		// 校验通过, session中存储userName, token
	// 如果登录成功, 重定向到首页
		// 否则  重定向到登录页面
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setCharacterEncoding(Constants.DEFAULT_CHARSET);
		resp.setHeader("Content-Type","text/html;charset=" + Constants.DEFAULT_CHARSET);
		
		String userName = (String) req.getAttribute(Constants.userName);
		String pwd = (String) req.getAttribute(Constants.pwd);
		JSONObject userInfo = new JSONObject().element("userName", userName).element("pwd", pwd).element("ip", Tools.getIPAddr(req) );
		StringBuilder sb = new StringBuilder();
		sb.append("an user attempt to login with : ");
		sb.append(userInfo.toString() );
		sb.append(", ");
		
		boolean isLogin = false;
		if((! Tools.isEmpty(userName)) && (! Tools.isEmpty(pwd)) ) {
			if(userName.equals(Constants.ACCOUNT) && pwd.equals(Constants.PWD)) {
				HttpSession session = req.getSession();
				session.setAttribute(Constants._ACCOUNT_NAME, Constants.ACCOUNT);
				session.setAttribute(Constants._TOKEN, Constants.TOKEN);
				session.setAttribute(Constants.preferInfo, new UserInfo(Constants.adminUserName, Constants.adminEmail, Constants.adminImageIdx, Tools.getPrivilege(Tools.isLogin(req))) );
				sb.append("login success !");
				isLogin = true;
			} else {
				sb.append("userName & password not match !");	
			}
		} else {
			sb.append("login failed !");
		}
		
		Tools.log(this, sb.toString() );
		if(isLogin) {
			resp.sendRedirect("/HXBlog/");
		} else {
			resp.sendRedirect("/HXBlog/login.html");
		}
	}
	
}
