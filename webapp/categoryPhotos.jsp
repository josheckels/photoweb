<%@page contentType="text/html" %>
<%@ taglib uri="/c" prefix="c" %>
<%@ taglib uri="/fmt" prefix="fmt" %>
<c:if test="${Photographer != null}">
    <p><font size="-1">Photographer: <c:out value="${Photographer.name}"/></font></p>
</c:if>

<!-- <p><font size="-1"><a href="/com.stampysoft.photoGallery.RichUI/RichUI.html#<c:out
    value="${Category.categoryId}"/>">View in new user interface</a></font></p> -->

<table width="100%">
    <tr>
        <td>
            <font size="-1">
                <c:forEach var="photo" items="${Photos}" varStatus="status">
                    <div
                        style="padding-bottom: 10px; padding-top: 10px; padding-right: 5px; padding-left: 5px; width: 225px; height: 275px; float: left; vertical-align: middle;">
                        <div
                            style="text-align: center; background-color: #F4F4F4; width: 225px; height: 275px; vertical-align: middle; padding: 3px;">
                            <a name="Photo<c:out value="${photo.photoId}" />"><a
                                href="photoDetail?PhotoId=<c:out value="${photo.photoId}" />&ReferringCategoryId=<c:out value="${Category.categoryId}"/>"><img
                                vspace="10"
                                height="<fmt:formatNumber value="${photo.thumbnailDimensions.height}"/>"
                                width="<fmt:formatNumber value="${photo.thumbnailDimensions.width}"/>"
                                src="<c:out value="${photo.thumbnailDimensions.URI}"/>"></a></a><br>
                            <c:out value="${photo.caption}"/> <c:if test="${photo.movie}"><a
                            href="<c:out value="${photo.movieURI}"/>"><img
                            src="/images/movie.gif" border="0"/></a></c:if>
                        </div>
                    </div>
                </c:forEach>
            </font>
        </td>
    </tr>
</table>
