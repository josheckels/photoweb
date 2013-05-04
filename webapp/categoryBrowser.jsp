<%@page contentType="text/html" %>
<%@ taglib uri="/c" prefix="c" %>
<%@ taglib uri="/fmt" prefix="fmt" %>

<html>
<head><title>Browsing Category: <c:out value="${Category.description}"/></title>
    <link rel="alternate" type="application/rss+xml" title="Josh Eckels' Homepage RSS Feed"
          href="http://www.jeckels.com/categoryRSS">

    <link href="stylesheet.css" type="text/css" rel="stylesheet"/>
</head>
<body>

    <h2>
        <a href="/">Home</a> -
        <c:forEach var="parentCategory" items="${PathToRoot}">
            <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${parentCategory.categoryId}"/>"><c:out
                value="${parentCategory.description}"/></a> -
        </c:forEach>
        <c:out value="${Category.description}"/>
    </h2>

    <c:if test="${Category.parentCategory != null}">
        <script language="javascript" type="text/javascript">
            <!--keyboard controls-->

            function keypress(e)
            {
                if (!e) var e = window.event;
                if (e.keyCode) keyCode = e.keyCode;
                else if (e.which) keyCode = e.which;
                if (!e.altKey && !e.ctrlKey && !e.shiftKey && keyCode == 67)
                {
                    window.location = "categoryBrowser?CategoryId=<c:out value="${Category.parentCategory.categoryId}"/>";
                    return false;
                }
                return true;
            }

            document.onkeydown = keypress;
        </script>
    </c:if>

    <% if (!((java.util.Collection) request.getAttribute("SubCategories")).isEmpty())
    { %>
    <h3>Subcategories</h3>
    <table width="100%">
        <tr>
            <td>
                <div style="margin: 0 auto;">
                    <font size="-1">
                        <c:forEach var="subCategory" items="${SubCategories}" varStatus="status">
                            <div
                                style="padding-bottom: 10px; padding-top: 10px; padding-right: 5px; padding-left: 5px; width: 225px; height: 275px; float: left; vertical-align: middle;">
                                <div
                                    style="text-align: center; background-color: #F4F4F4; width: 225px; height: 275px; vertical-align: middle; padding: 3px;">
                                    <a name="Category<c:out value="${category.categoryId}" />"><a
                                        href="categoryBrowser?CategoryId=<c:out value="${subCategory.categoryId}"/>">
                                        <c:if test="${subCategory.defaultPhoto != null }"><img border="0" vspace="10"
                                                                                               height="<fmt:formatNumber value="${subCategory.defaultPhoto.thumbnailDimensions.height}"/>"
                                                                                               width="<fmt:formatNumber value="${subCategory.defaultPhoto.thumbnailDimensions.width}"/>"
                                                                                               src="<c:out value="${subCategory.defaultPhoto.thumbnailDimensions.URI}"/>"></c:if><br>
                                        <b><c:out value="${subCategory.description}"/></b></a></a>
                                </div>
                            </div>
                        </c:forEach>
                    </font>
                </div>
            </td>
        </tr>
    </table>
    <% } %>
    <p/>
    <hr style="float: none;"/>

    <jsp:include page="categoryPhotos.jsp"/>
    <jsp:include page="/license.jsp"/>

</body>
</html>
