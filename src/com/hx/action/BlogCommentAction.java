package com.hx.action;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import com.hx.bean.Blog;
import com.hx.bean.Comment;
import com.hx.bean.ResponseMsg;
import com.hx.bean.UserInfo;
import com.hx.business.BlogManager;
import com.hx.business.CommentManager;
import com.hx.util.Constants;
import com.hx.util.Tools;

public class BlogCommentAction extends HttpServlet {
	
	// 处理评论相关业务
	// 获取blogIdx, floorIdx, imageIdx  如果发生异常, 将imageIdx置空 [必然过不了校验]
	// 校验blogIdx, floorIdx, imageIdx, userInfo, commentBody
	// 再校验这个播客是否存在
	// 校验通过后, 设置用户的userInfo
		// 如果comment是新的楼层信息, 则确定他的楼层数[index]
			// 否则  便是回复, 确定它在该楼层的回复数[index]
		// 将comment信息添加到CommentManager中
	// 返回 响应结果, 记录日志
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setCharacterEncoding(Constants.DEFAULT_CHARSET);
		resp.setHeader("Content-Type","text/html;charset=" + Constants.DEFAULT_CHARSET);
		
		ResponseMsg respMsg = new ResponseMsg();
		Integer blogIdx = null;
		Integer floorIdx = null;
		Integer imageIdx = null;
		try {
			blogIdx = Integer.parseInt(req.getParameter("blogIdx") );
			floorIdx = Integer.parseInt(req.getParameter("floorIdx") );
			imageIdx = Integer.parseInt(req.getParameter("imageIdx") );
		} catch (Exception e) {
			imageIdx = null;
		}
		String to = req.getParameter("to");
		if(Tools.isEmpty(to)) {
			to = Constants.defaultTo;
		}
		String commentBody = req.getParameter("comment");
		Comment comment = null;
		if(Tools.validateObjectBeNull(req, blogIdx, "blogIdx", respMsg) ) {
			if(Tools.validateObjectBeNull(req, floorIdx, "floorIdx", respMsg) ) {
				if(Tools.validateObjectBeNull(req, imageIdx, "imageIdx", respMsg) ) {
					UserInfo userInfo = new UserInfo(req.getParameter("userName"), req.getParameter("email"), imageIdx, Tools.getPrivilege(Tools.isLogin(req)) );
					if(Tools.validateTitle(req, to, "to's userName", respMsg) ) {					
						if(Tools.validateUserInfo(req, userInfo, respMsg) ) {
							if(Tools.validateCommentBody(req, commentBody, respMsg) ) {
								Blog blogInServer = BlogManager.getBlog(blogIdx);
								if(Tools.validateBlog(req, blogInServer, respMsg)) {
									req.getSession().setAttribute(Constants.preferInfo, userInfo);
									comment = new Comment(blogIdx, floorIdx, Constants.defaultCommentIdx, userInfo, Constants.createDateFormat.format(new Date()), to, Tools.replaceCommentBody(commentBody, Constants.scriptCharacterMap) );
									try {
										CommentManager.addComment(blogIdx, comment);
										if(! Tools.isReply(commentBody) ) {
											CommentManager.updateFloorIdx(comment);
										} else {
											comment.setComment(Tools.getReplyComment(commentBody));
											CommentManager.updateCommentIdx(comment);
										}
									} catch (Exception e) {
										e.printStackTrace();
									}
									respMsg.set(Constants.respSucc, Constants.defaultResponseCode, "comment success !", Tools.getIPAddr(req) );
								}
							}
						}
					}
				}
			}
		}
		
		JSONObject res = new JSONObject();
		res.element("respMsg", respMsg.toString() );
		res.element("comment", String.valueOf(comment) );
		
		PrintWriter out = resp.getWriter();
		String respInfo = res.toString();
		out.write(respInfo );
		Tools.log(this, respInfo);
		out.close();
	}
	
}
