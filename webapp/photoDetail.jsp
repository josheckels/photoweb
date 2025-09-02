<%@ page import="com.stampysoft.photoGallery.servlet.PreviewImage" %>
<%@ page import="java.util.List" %>
<%@ page import="com.stampysoft.photoGallery.Comment" %>
<%@ page import="java.util.Set" %>
<%@ page import="com.stampysoft.photoGallery.common.Resolution" %>
<%@page contentType="text/html" %>
<%@ taglib uri="/c" prefix="c" %>
<%@ taglib uri="/fmt" prefix="fmt" %>

<jsp:useBean id="PreviousPreviews" scope="request" type="java.util.List<com.stampysoft.photoGallery.Photo>"/>
<jsp:useBean id="NextPreviews" scope="request" type="java.util.List<com.stampysoft.photoGallery.Photo>"/>
<jsp:useBean id="photo" scope="request" type="com.stampysoft.photoGallery.Photo"/>
<jsp:useBean id="Category" scope="request" type="com.stampysoft.photoGallery.Category" />
<jsp:useBean id="UserCommentError" scope="request" type="java.lang.String" />

<html>
<head><title>Photo Detail:
<c:out value="${photo.caption}"/></title>
    <link rel="alternate" type="application/rss+xml" title="Josh Eckels Homepage RSS Feed"
          href="http://www.jeckels.com/categoryRSS">

    <link href="stylesheet.css" type="text/css" rel="stylesheet"/>

    <script language="javascript" type="text/javascript">
        <!--keyboard controls-->

        var shortcutsEnabled = true;
        var captchaShown = false;

        function disableKeyboardShortcuts()
        {
            showCaptcha();
            shortcutsEnabled = false;
        }

        function showCaptcha()
        {
            if (!captchaShown)
            {
                captchaShown = true;
                document.getElementById('captchaImage').innerHTML = '<img src="/kaptcha.jpg" height="50" width="200">';
                document.getElementById('captchaTextLabel').innerHTML = '<font size="-1">Verification text:</font>';
                document.getElementById('captchaTextField').innerHTML = '<input type="text" name="kaptcha" value="" size="8" onfocus="disableKeyboardShortcuts()" onblur="enableKeyboardShortcuts()" />';
            }
        }

        function enableKeyboardShortcuts()
        {
            shortcutsEnabled = true;
        }

        function keypress(e)
        {
            if (!shortcutsEnabled)
            {
                return;
            }

            if (!e) var e = window.event;
            if (e.keyCode) keyCode = e.keyCode;
            else if (e.which) keyCode = e.which;
            if (!e.altKey && !e.ctrlKey && !e.shiftKey)
            {
                switch (keyCode)
                    {
                    <%
             List<PreviewImage> previousPreviews = (List<PreviewImage>)request.getAttribute("PreviousPreviews");
             List<PreviewImage> nextPreviews = (List<PreviewImage>)request.getAttribute("NextPreviews");
             if (!previousPreviews.isEmpty()) { %>
                    case 37: window.location = "photoDetail?PhotoId=<%= previousPreviews.get(previousPreviews.size() - 1).getPhotoId() %>&ReferringCategoryId=<c:out value="${Category.categoryId}" />"; return false; break;
                    <% }
                 if (!nextPreviews.isEmpty()) { %>
                    case 39:  window.location = "photoDetail?PhotoId=<%= nextPreviews.get(0).getPhotoId() %>&ReferringCategoryId=<c:out value="${Category.categoryId}" />"; return false; break;
                    <% } %>
                    <c:if test="${Category != null}">
                    case 67:  window.location = "categoryBrowser?CategoryId=<c:out value="${Category.categoryId}"/>#Photo<c:out value="${photo.photoId}"/>"; return false; break;
                    </c:if>
                }
            }
            return true;
        }

        document.onkeydown = keypress;
    </script>
</head>
<body>
<table align="center" cellpadding="5" cellspacing="0" border="0">
    <tr><td align="center" colspan="5"><font size="-1" color="red"><c:out value="${UserCommentError}" /></font></td></tr>
    <tr>
        <td valign="center" align="left" width="90" rowspan="2">
            <table align="center" cellpadding="8" cellspacing="4">
                <c:forEach var="previousPreview" items="${PreviousPreviews}">
                    <tr>
                        <td align="center">
                            <a href="photoDetail?PhotoId=<c:out value="${previousPreview.photoId}" />&ReferringCategoryId=<c:out value="${Category.categoryId}" />"><img
                                src="<c:out value="${previousPreview.preview.URI}"/>"
                                height="<c:out value="${previousPreview.renderDimensions.height}"/>"
                                width="<c:out value="${previousPreview.renderDimensions.width}"/>"
                                title="<c:out value="${previousPreview.caption}"/>" valign="center" align="center"></a>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </td>
        <td bgcolor="#F0F0F0">
        </td>
        <td align="center" bgcolor="#F0F0F0">
            <img src="/images/clear.gif" id="horizontalSpacer" width="700" height="1" /><br/>
            <a href="<c:out value="${photo.originalDimensions.URI}"/>"><img id="photo" vspace="0"
                                                                            height="<fmt:formatNumber value="${photo.defaultDimensions.height}"/>"
                                                                            width="<fmt:formatNumber value="${photo.defaultDimensions.width}"/>"
                                                                            src="<c:out value="${photo.defaultDimensions.URI}"/>"/></a>
        </td>
        <td bgcolor="#F0F0F0">
        </td>
        <td valign="center" align="right" width="90" rowspan="3">
            <table align="center" cellpadding="8">
                <c:forEach var="nextPreview" items="${NextPreviews}">
                    <tr>
                        <td align="center">
                            <a href="photoDetail?PhotoId=<c:out value="${nextPreview.photoId}" />&ReferringCategoryId=<c:out value="${Category.categoryId}" />"><img
                                src="<c:out value="${nextPreview.preview.URI}"/>"
                                height="<c:out value="${nextPreview.renderDimensions.height}"/>"
                                width="<c:out value="${nextPreview.renderDimensions.width}"/>"
                                valign="center"
                                title="<c:out value="${nextPreview.caption}"/>"
                                alt="<c:out value="${nextPreview.caption}"/>"
                                align="center" /></a>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </td>
    </tr>
    <tr>
        <td align="center" bgcolor="#F0F0F0" colspan="3">
            <b><c:out value="${photo.caption}"/></b>
            <c:if test="${photo.movie}"><br/><a href="<c:out value="${photo.movieURI}"/>">View Movie</a></c:if>
        </td>
    </tr>
    <tr>
        <td/>
        <td colspan="3" align="center" bgcolor="#F0F0F0">
            <c:if test="${Category != null}">
                <% if (!previousPreviews.isEmpty())
                { %>
                <a href="photoDetail?PhotoId=<%= previousPreviews.get(previousPreviews.size() - 1).getPhotoId() %>&ReferringCategoryId=<c:out value="${Category.categoryId}" />"><img
                    src="/images/previous.gif" alt="Previous" class="noborder"
                    title="Previous image (or hit the left arrow key)" align="absmiddle" /></a>
                <% }
                else
                { %>
                <img src="/images/previousDisabled.gif" border="0" align="absmiddle" />
                <% } %>
                &nbsp;&nbsp;
                <font size="-1">
                    <a href="/">Home</a>:
                    <c:forEach var="parentCategory" items="${Category.pathToRoot}" varStatus="status">
                        <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${parentCategory.categoryId}"/>"><c:out
                            value="${parentCategory.description}"/></a>:
                    </c:forEach>

                    <a title="Return to category (or hit C)" href="categoryBrowser?CategoryId=<c:out value="${Category.categoryId}"/>#Photo<c:out value="${photo.photoId}"/>">
                        <c:out value="${Category.description}"/></a>
                </font>
                &nbsp;&nbsp;
                <% if (!nextPreviews.isEmpty())
                { %>
                <a href="photoDetail?PhotoId=<%= nextPreviews.get(0).getPhotoId() %>&ReferringCategoryId=<c:out value="${Category.categoryId}" />"><img
                    src="/images/next.gif" alt="Next" border="0" align="absmiddle"
                    title="Next image (or hit the right arrow key)" class="noborder" /></a>
                <% }
                else
                { %>
                <img src="/images/nextDisabled.gif" border="0" align="absmiddle" />
                <% } %>
            </c:if>
        </td>
    </tr>
</table>
<p/>
<table align="center">
    <tr>
        <td width="50%" valign="top">
            <table align="center">
                <tr>
                    <td align="center" colspan="2"><font size="-1"><b>Photo data</b></font></td>
                </tr>
                <tr>
                    <td valign="top"><font size="-2">All categories:</font></td>
                    <td><font size="-2">
                        <c:forEach var="category" items="${Categories}">
                            <a href="/">Home</a>:
                            <c:forEach var="parentCategory" items="${category.pathToRoot}" varStatus="status">
                                <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${parentCategory.categoryId}"/>"><c:out
                                    value="${parentCategory.description}"/></a>:
                            </c:forEach>
                            <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${category.categoryId}"/>#Photo<c:out value="${photo.photoId}"/>"><c:out
                                value="${category.description}"/></a><br>
                        </c:forEach>
                    </font></td>
                </tr>
                <tr>
                    <td><font size="-2">Photographer:</font></td>
                    <td><font size="-2"><c:out value="${Photographer.name}"/></font></td>
                </tr>
                <c:forEach var="tag" items="${photo.displayMetadata}">
                    <tr>
                        <td><font size="-2"><c:out value="${tag.tagName}"/>:</font></td>
                        <td><font size="-2"><c:out value="${tag.description}"/></font></td>
                    </tr>
                </c:forEach>
                <tr>
                    <td><font size="-2">Resolutions:</font></td>
                    <td>
                        <font size="-2">
                            <c:forEach var="dimension" items="${photo.possibleDimensions}" varStatus="status">
                                <a href="<c:out value="${dimension.URI}"/>"><fmt:formatNumber
                                    value="${dimension.width}"/>x<fmt:formatNumber value="${dimension.height}"/></a><c:if
                                test="${status.last == false}"> | </c:if>
                            </c:forEach>
                            <c:if test="${photo.movie}">| <a href="<c:out value="${photo.movieURI}"/>">Movie</a></c:if>
                        </font>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
<jsp:include page="/license.jsp"/>

<p align="center">
    <font size="-2">
        <strong>Keyboard navigation:</strong>
        Next photo (right arrow),
        Previous photo (left arrow),
        Return to category (C)
    </font>
</p>

</body>
<script type="text/javascript">
    <%
    String error = (String)request.getAttribute("UserCommentError");
    if (error != null && error.length() > 0)
        { %>
        showCaptcha();
    <% } %>

var availableWidths = new Array();
var availableHeights = new Array();
var availableURLs = new Array();
<%
for (int i = 0; i < photo.getPossibleDimensions().length; i++)
{
    Resolution resolution = photo.getPossibleDimensions()[i]; %>
    availableWidths[<%= i %>] = <%= resolution.getWidth() %>;
    availableHeights[<%= i %>] = <%= resolution.getHeight() %>;
    availableURLs[<%= i %>] = '<%= resolution.getURI() %>';<%
}
%>
    
function chooseImageSize()
{
    var myWidth = 0, myHeight = 0;

    if( typeof( window.innerWidth ) == 'number' )
    {
        //Non-IE
        myWidth = window.innerWidth;
        myHeight = window.innerHeight;
    }
    else if( document.documentElement && ( document.documentElement.clientWidth || document.documentElement.clientHeight ) )
    {
        //IE 6+ in 'standards compliant mode'
        myWidth = document.documentElement.clientWidth;
        myHeight = document.documentElement.clientHeight;
    }
    else if( document.body && ( document.body.clientWidth || document.body.clientHeight ) )
    {
        //IE 4 compatible
        myWidth = document.body.clientWidth;
        myHeight = document.body.clientHeight;
    }

    var l = availableWidths.length - 1;

    var maxWidth = myWidth - 290;
    if (maxWidth < 700 && availableWidths[l] > availableHeights[l])
    {
        maxWidth = 700;
    }

    var maxHeight = myHeight - 100;
    if (maxHeight < 700 && availableHeights[l] > availableWidths[l])
    {
        maxHeight = 700;
    }

    var pixelDensity = window.devicePixelRatio;
    if (!pixelDensity)
    {
        pixelDensity = 1;
    }

    var bestIndex = 0;
    for (var i = 0; i < availableHeights.length; i++)
    {
        if (availableHeights[i] <= (maxHeight * pixelDensity) && availableWidths[i] <= (maxWidth *  pixelDensity) &&
            availableHeights[i] > availableHeights[bestIndex] && availableWidths[i] > availableWidths[bestIndex])
        {
            bestIndex = i;
        }
    }

    document.getElementById('horizontalSpacer').width = maxWidth;

    if (bestIndex < availableHeights.length - 1 && availableHeights[bestIndex] != maxHeight * pixelDensity && availableWidths[bestIndex] != maxWidth * pixelDensity)
    {
        bestIndex++;
    }

    var photoElement = document.getElementById('photo');

    if (true)
    {
        if (maxWidth > maxHeight)
        {
            var h = Math.floor(maxWidth * (availableHeights[l] / availableWidths[l]));
            if (h <= maxHeight)
            {
                photoElement.width = maxWidth;
                photoElement.height = Math.floor(maxWidth * (availableHeights[l] / availableWidths[l]));
            }
            else
            {
                photoElement.height = maxHeight;
                photoElement.width = Math.floor(maxHeight * (availableWidths[l] / availableHeights[l]));
            }
        }
        else
        {
            var w = Math.floor(maxHeight * (availableWidths[l] / availableHeights[l]));
            if (w <= maxWidth)
            {
                photoElement.height = maxHeight;
                photoElement.width = Math.floor(maxHeight * (availableWidths[l] / availableHeights[l]));
            }
            else
            {
                photoElement.width = maxWidth;
                photoElement.height = Math.floor(maxWidth * (availableHeights[l] / availableWidths[l]));
            }
        }
    }
    else
    {
        photoElement.width = availableWidths[bestIndex];
        photoElement.height = availableHeights[bestIndex];
    }

    photoElement.src = availableURLs[bestIndex];
}
    
    chooseImageSize();
    window.onresize = chooseImageSize;
</script>
</html>
