package com.hx.blog.action;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import com.hx.blog.interf.BaseAction;
import com.hx.blog.util.Constants;
import com.hx.blog.util.Log;
import com.hx.blog.util.Tools;

// ��ȡblog���õ�action
public class BlogResumeAction extends BaseAction {
	
	// �Ƿ��ǵ�һ�η���
	private static boolean isFirst = true;
	
	// ��ȡresume.conf�е�����
	// ���ڼ��������ݱȽ϶�, ����д��־�Ͳ�д������
	// ���resumePath��Ӧ���ļ�������, ���ȡ�ڴ��л����config
		// ����  ֱ�ӻ�ȡ��Ӧ���ļ�������, ���浽defaultConfig, ֮��ȡ����, ��defaultConfig��ȡ
	// ���� ��Ӧ���, ��¼��־	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setCharacterEncoding(Constants.DEFAULT_CHARSET);
		resp.setHeader("Content-Type","text/html;charset=" + Constants.DEFAULT_CHARSET);

		String content = null;
		String logInfo = null;
		if((isFirst) && (Tools.isFileExists(Tools.getPackagePath(Tools.getProjectPath(), Constants.configPath))) ) {
			content = Tools.getContent(Tools.getPackagePath(Tools.getProjectPath(), Constants.resumePath), Constants.DEFAULT_CHARSET );
			Constants.defaultResume = JSONObject.fromObject(content);
			logInfo = "your resume be seen by : " + Tools.getIPAddr(req) + ", read from 'resume.json' !";
		} else {
			content = Constants.defaultResume.toString();
			logInfo = "your resume be seen by : " + Tools.getIPAddr(req);
		}
		isFirst = false;
		
		PrintWriter out = resp.getWriter();
		out.write(content);
		Tools.log(this, logInfo);
		out.close();
	}
	
}