<%@ page import="com.stampysoft.photoGallery.servlet.PhotoDetail" %>
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
    <link rel="alternate" type="application/rss+xml" title="Josh Eckels' Homepage RSS Feed"
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
             List<PhotoDetail.PreviewImage> previousPreviews = (List<PhotoDetail.PreviewImage>)request.getAttribute("PreviousPreviews");
             List<PhotoDetail.PreviewImage> nextPreviews = (List<PhotoDetail.PreviewImage>)request.getAttribute("NextPreviews");
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
<% if (Boolean.TRUE.equals(request.getSession(true).getAttribute("ShowAds")))
{ %>
<div align="center">
    <script type="text/javascript"><!--
    google_ad_client = "pub-0439692010885772";
    google_ad_width = 468;
    google_ad_height = 60;
    google_ad_format = "468x60_as";
    google_ad_type = "text";
    google_ad_channel = "";
    google_color_border = "CCCCCC";
    google_color_bg = "FFFFFF";
    google_color_link = "000000";
    google_color_url = "666666";
    google_color_text = "333333";
    //--></script>
    <script type="text/javascript"
            src="http://pagead2.googlesyndication.com/pagead/show_ads.js">
    </script>
    <br/>
</div>
<% } %>
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
                                height="<c:out value="${previousPreview.preview.height}"/>"
                                width="<c:out value="${previousPreview.preview.width}"/>"
                                title="<c:out value="${previousPreview.caption}"/>" valign="center" align="center"></a>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </td>
        <td bgcolor="#F0F0F0">
            <img src="/images/clear.gif" id="verticalSpacer1" width="1" height="700" />
        </td>
        <td align="center" bgcolor="#F0F0F0">
            <img src="/images/clear.gif" id="horizontalSpacer" width="700" height="1" /><br/>
            <a href="<c:out value="${photo.originalDimensions.URI}"/>"><img id="photo" vspace="0"
                                                                            height="<fmt:formatNumber value="${photo.defaultDimensions.height}"/>"
                                                                            width="<fmt:formatNumber value="${photo.defaultDimensions.width}"/>"
                                                                            src="<c:out value="${photo.defaultDimensions.URI}"/>"/></a>
        </td>
        <td bgcolor="#F0F0F0">
            <img src="/images/clear.gif" id="verticalSpacer2" width="1" height="700" />
        </td>
        <td valign="center" align="right" width="90" rowspan="3">
            <table align="center" cellpadding="8">
                <c:forEach var="nextPreview" items="${NextPreviews}">
                    <tr>
                        <td align="center">
                            <a href="photoDetail?PhotoId=<c:out value="${nextPreview.photoId}" />&ReferringCategoryId=<c:out value="${Category.categoryId}" />"><img
                                src="<c:out value="${nextPreview.preview.URI}"/>"
                                height="<c:out value="${nextPreview.preview.height}"/>"
                                width="<c:out value="${nextPreview.preview.width}"/>"
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
                <tr>
                    <td />
                    <td><font size="-2"><input type="checkbox" id="stretchImage" onclick="toggleStretch()"/>&nbsp;Stretch image to fill space</font></td>
                </tr>
            </table>
        </td>
        <td width="50%" valign="top">
            <table align="center">
                <tr>
                    <td align="center" colspan="2"><font size="-1"><b><a name="comments">Comments</a></b></font></td>
                </tr><%
                Set<Comment> comments = (Set<Comment>)request.getAttribute("Comments");
                if (comments.isEmpty())
                { %>
                <tr>
                    <td><font size="-1" colspan="2"><i>No comments on this photo</i></font></td>
                </tr><%
                } %>
                <c:forEach var="comment" items="${Comments}">
                    <tr>
                        <td valign="top"><font size="-1"><c:out value="${comment.name}" /> on <fmt:formatDate value="${comment.createdOn}" />:&nbsp;</font></td>
                        <td><font size="-1"><c:out value="${comment.comment}" /></font></td>
                    </tr>
                </c:forEach>
            </table>

            <form method="post">
                <input type="hidden" name="requestedAction" value="submitComment" />
                <table align="center">
                    <tr>
                        <td align="center" colspan="2"><font size="-1" color="red"><c:out value="${UserCommentError}" /></font></td>
                    </tr>
                    <tr>
                        <td align="right"><font size="-1">Your name:</font></td>
                        <td><input type="text" onfocus="disableKeyboardShortcuts()" onblur="enableKeyboardShortcuts()" name="name" size="30" value="<c:out value="${UserComment.name}" />" /></td>
                    </tr>
                    <tr>
                        <td align="right"><font size="-1">Your email address:</font></td>
                        <td><input type="text" onfocus="disableKeyboardShortcuts()" onblur="enableKeyboardShortcuts()" name="email" size="30" value="<c:out value="${UserComment.email}" />" /></td>
                    </tr>
                    <tr>
                        <td valign="top" align="right"><font size="-1">Comment:</font></td>
                        <td><textarea cols="26" onfocus="disableKeyboardShortcuts()" onblur="enableKeyboardShortcuts()" name="comment" rows="3"><c:out value="${UserComment.comment}" /></textarea></td>
                    </tr>
                    <tr>
                        <td />
                        <td id="captchaImage" />
                    </tr>
                    <tr>
                        <td align="right" id="captchaTextLabel" />
                        <td id="captchaTextField" />
                    </tr>
                    <tr>
                        <td/><td><input type="submit" value="Submit Comment" /></td>
                    </tr>
                </table>
            </form>
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

    var bestIndex = 0;
    for (var i = 0; i < availableHeights.length; i++)
    {
        if (availableHeights[i] <= maxHeight && availableWidths[i] <= maxWidth &&
            availableHeights[i] > availableHeights[bestIndex] && availableWidths[i] > availableWidths[bestIndex])
        {
            bestIndex = i;
        }
    }

    document.getElementById('horizontalSpacer').width = maxWidth;
    document.getElementById('verticalSpacer1').height = maxHeight;
    document.getElementById('verticalSpacer2').height = maxHeight;

    var photoElement = document.getElementById('photo');

    if (document.getElementById('stretchImage').checked)
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
    
    function createCookie(value) {
        var date = new Date();
        date.setTime(date.getTime()+(365*24*60*60*1000));
        var expires = "; expires="+date.toGMTString();
        document.cookie = "stretchImage="+value+expires+"; path=/";
    }

    function readCookie() {
        var nameEQ = "stretchImage=";
        var ca = document.cookie.split(';');
        for(var i=0;i < ca.length;i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1,c.length);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
        }
        return false;
    }

    function toggleStretch()
    {
        createCookie(document.getElementById('stretchImage').checked == true);
        chooseImageSize();
    }

    document.getElementById('stretchImage').checked = (readCookie() == 'true');
    chooseImageSize();
    window.onresize = chooseImageSize;
</script>
</html>
