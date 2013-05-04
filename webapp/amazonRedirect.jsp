<%
    String asin = request.getParameter("asin");
    if (asin == null)
    {
        response.sendRedirect("http://www.amazon.com/exec/obidos/redirect-home/josheckelsamazon/");
    }
    else
    {
        int index = asin.indexOf("ASIN/");
        if (index != -1)
        {
            asin = asin.substring(index + "ASIN/".length());
            asin = asin.substring(0, asin.indexOf("/"));
        }
        index = asin.indexOf("/-/");
        if (index != -1)
        {
            asin = asin.substring(index + "/-/".length());
            asin = asin.substring(0, asin.indexOf("/"));
        }
        index = asin.indexOf("/product/");
        if (index != -1)
        {
            asin = asin.substring(index + "/product/".length());
            asin = asin.substring(0, asin.indexOf("/"));
        }

//	response.sendRedirect( "http://www.jeckels.com/homepage?asin=" + asin + "/josheckelsamazon" );
        response.sendRedirect("http://www.amazon.com/exec/obidos/ASIN/" + asin + "/josheckelshom-20");
    }
%>