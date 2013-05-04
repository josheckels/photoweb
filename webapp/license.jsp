<%@ page import="com.stampysoft.photoGallery.servlet.PhotoDetail" %>
<%@ page import="java.util.List" %>
<p>&nbsp;</p>

<p align="center">
    <font size="-2">
        <% if (request.getAttribute("Photographer") != null)
        { %>
        <%= ((com.stampysoft.photoGallery.Photographer) request.getAttribute("Photographer")).getCopyright() %>
        <% } %>
    </font>
</p>

<script type="text/javascript">
    var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
    document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
</script>
<script type="text/javascript">
try
{
    var pageTracker = _gat._getTracker("UA-82411-1");
    pageTracker._trackPageview();
}
catch(err) {}
</script>