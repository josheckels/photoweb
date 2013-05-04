package com.stampysoft.servlet;

import com.stampysoft.photoGallery.ResolutionUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class AdFilter implements Filter
{

    public static final String SHOW_ADS_PROPERTY = "ShowAds";

    public void init(FilterConfig config) throws ServletException
    {
        ResolutionUtil.init();
    }

    public void doFilter(ServletRequest req, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpSession session = request.getSession(true);

        String param = request.getParameter("showads");
        if (param != null)
        {
            session.setAttribute(SHOW_ADS_PROPERTY, Boolean.valueOf("true".equals(param)));
        }
        else
        {
            if (session.getAttribute(SHOW_ADS_PROPERTY) == null)
            {
                session.setAttribute(SHOW_ADS_PROPERTY, Boolean.FALSE);
/*				String referrer = request.getHeader("Referer");
				String agent = request.getHeader("User-Agent");
				boolean google = agent != null && agent.toLowerCase().indexOf("googlebot") != -1;
				boolean show = google || (referrer != null && referrer.length() > 2 && !"true".equals(request.getParameter("fromrss")));
				session.setAttribute(SHOW_ADS_PROPERTY, Boolean.valueOf(show));
				*/
            }
        }

        chain.doFilter(req, response);
    }

    public void destroy()
    {
    }

}
