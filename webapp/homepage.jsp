<%@page contentType="text/html" %>
<%@ taglib uri="/c" prefix="c" %>
<%@ taglib uri="/fmt" prefix="fmt" %>

<html>
<head>
    <title>Josh Eckels' Homepage</title>
    <link rel="alternate" type="application/rss+xml" title="Josh Eckels Homepage RSS Feed"
          href="http://www.jeckels.com/categoryRSS">

    <link href="stylesheet.css" type="text/css" rel="stylesheet"/>
</head>
<body>
    <h1 align="center">Josh Eckels Homepage</h1>

<table>
    <tr>
        <td>
            <h2>Photo Gallery</h2>
            <p>
                It's a delightful mix of people doing embarrassing things and some pretty pictures.
                The top-level categories are:
            </p>
            <ul>
                <jsp:useBean id="Subcategories" scope="request" type="java.util.List<com.stampysoft.photoGallery.Category>"/>
                <c:forEach var="subCategory" items="${Subcategories}">
                    <li><a href="/category/<c:out value="${subCategory.categoryId}"/>"><c:out
                        value="${subCategory.description}"/></a></li>
                </c:forEach>
            </ul>
            <p>
                See all the categories as a <a href="/categoryList">flat list</a>.
            </p>
            <p>
                The newest categories are:
            </p>
            <ul>
                <jsp:useBean id="NewestCategories" scope="request" type="java.util.List<com.stampysoft.photoGallery.Category>"/>
                <c:forEach var="newCategory" items="${NewestCategories}">
                    <li>
                        <c:forEach var="parentCategory" items="${newCategory.pathToRoot}" varStatus="status">
                            <a href="/category/<c:out default="" value="${parentCategory.categoryId}"/>"><c:out
                                value="${parentCategory.description}"/></a>:
                        </c:forEach>
                        <a href="category/<c:out default="" value="${newCategory.categoryId}"/>"><c:out
                            value="${newCategory.description}"/></a>
                        (added <fmt:formatDate value="${newCategory.createdOn}" pattern="EEEE, MMMM d"/>)
                    </li>
                </c:forEach>
            </ul>
            <p>
            Some randomly chosen selections from the wallpaper category are shown below.
            </p>
            <h2>RSS Feed</h2>
            <p>
                Do you find yourself furiously reloading to catch the latest updates? Save
                your fingers and just subscribe to my RSS feed for <a href="/categoryRSS">new categories</a>.
            </p>

            <h2>Work</h2>
            <p>
                I work at <a href="http://www.labkey.com/">LabKey Software</a> on <a href="http://www.labkey.org/">LabKey Server</a>.
            </p>

            <h2>J2ME Ant Tasks</h2>
            <p>
                I've written a <a href="/ant">set of Ant tasks</a> that help automate building Java 2, Micro Edition
                (J2ME) projects.
            </p>

            <h2>Ads, sweet nourishing ads</h2>
            <p>
                And finally, since no web site is complete without at least one advertisement,
                if you're planning on buying something from Amazon, if you go to their site
                through <a href="http://www.amazon.com/exec/obidos/redirect-home/josheckelshom-20/">this link</a>
                and purchase something I'll get some referral money.
            </p>

            <h2>Contact info</h2>
            <p>
                If, for some reason, you would like to get in touch with me, you can e-mail me at
                <a href="mailto:josh@jeckels.com">josh@jeckels.com</a>.
            </p>
        </td>
    </tr>
</table>
<jsp:include page="/license.jsp"/>
</body>
</html>
