<%@page contentType="text/html" %>
<%@ taglib uri="/c" prefix="c" %>
<%@ taglib uri="/fmt" prefix="fmt" %>

<html>
<head><title>Category List</title>
    <link rel="alternate" type="application/rss+xml" title="Josh Eckels' Homepage RSS Feed"
          href="http://www.jeckels.com/categoryRSS">

    <link href="stylesheet.css" type="text/css" rel="stylesheet"/>
</head>
<body>

    <h2>
        Category List
    </h2>

    <p>
        <jsp:useBean id="DescriptionSort" scope="request" type="java.lang.Boolean"/>
        <jsp:useBean id="CreationSort" scope="request" type="java.lang.Boolean"/>
        <c:if test="${DescriptionSort}">Currently sorted by description, <a href="?sort=creation">sort by creation date</a>.</c:if>
        <c:if test="${CreationSort}">Currently sorted by creation date, <a href="?sort=description">sort by description</a>.</c:if>
        <a href="/">Return to homepage</a>.
    </p>

    <jsp:useBean id="Categories" scope="request" type="java.util.List<com.stampysoft.photoGallery.Category>"/>
    <c:forEach var="category" items="${Categories}">
        <c:forEach var="parentCategory" items="${category.pathToRoot}">
            <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${parentCategory.categoryId}"/>"><c:out
                value="${parentCategory.description}"/></a>:
        </c:forEach>
        <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${category.categoryId}"/>"><c:out value="${category.description}"/></a>
        <br />
    </c:forEach>

    <jsp:include page="/license.jsp"/>

</body>
</html>
