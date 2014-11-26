<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Specify Parameters</title>

  <script src="http://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>

  <!-- Load in JQuery UI javascript and css to set general look and feel -->
  <script src="/api/jquery-ui/jquery-ui.js"></script>
  <link rel="stylesheet" href="/api/jquery-ui/jquery-ui.css">
  
  <!-- Load in Select2 files so can create fancy selectors -->
  <link href="/api/select2/select2.css" rel="stylesheet"/>
  <script src="/api/select2/select2.min.js"></script>
  
  <script>
  // Enable JQuery tooltips. In order to use html in tooltip need to 
  // specify content function. Turning off 'focusin' events is important
  // so that tooltip doesn't popup again if previous or next month
  // buttons are clicked in a datepicker.
  $(function() {
	  $( document ).tooltip({
          content: function () {
              return $(this).prop('title');
          }
      }).off('focusin');
	  });
  </script>

  <style>
  #mainDiv {margin-left: auto; margin-right: auto; width: 600px;}
  body {font-family: sans-serif; font-size: large;}
  #title {font-weight: bold; font-size: x-large; 
          margin-top: 40px; margin-bottom: 20px;
          margin-left: auto; margin-right: auto; width: 70%; text-align: center;}
  input, select {font-size: large;}
  label {width: 200px; float: left; text-align: right; margin-top: 4px; margin-right: 10px;}
  .param {margin-top: 10px;}
  #route {width:300px;}
  #submit {margin-top: 40px; margin-left: 200px;}
  .note {font-size: small;}

  /* Make input widgets different color */
  input, select {
  	background-color: #f8f8f8;
  }

  .ui-tooltip {
	/* Change background color of tooltips a bit and use a reasonable font size */
  	background: #F7EEAB;
	font-size: small;
	padding: 4px;
  }
  </style>
</head>
<body>

   <div id="title">
   Select Parameters for Prediction Accuracy Scatter Chart
   </div>
   
<div id="mainDiv">
<form action="predAccuracyScatterChart.jsp" method="POST">
   <%-- For passing agency param to the report --%>
   <input type="hidden" name="a" value="<%= request.getParameter("a")%>">
   
   <jsp:include page="params/fromToDateTime.jsp" />
   
   <jsp:include page="params/route.jsp" />

   <jsp:include page="params/boolean.jsp">
    <jsp:param name="label" value="Provide tooltip info"/>
    <jsp:param name="name" value="tooltips"/>
    <jsp:param name="default" value="false"/>
    <jsp:param name="tooltip" value="If set to True then provides detailed 
      information on data through tooltip. Can be useful but if processing 
      large amounts of data can slow down the query."/>
   </jsp:include>
 
   <div class="param">
     <label for="source">Prediction Source:</label> 
     <select id="source" name="source" 
     	title="Specifies which prediction system to display data for. Selecting
     	'Transitime' means will only show prediction data generated by Transitime. 
     	If there is another prediction source then can select 'Other'. And selecting 'All'
     	displays data for all prediction sources.">
       <option value="Transitime">Transitime</option>
       <option value="Other">Other</option>
       <option value="">All</option>
     </select>
   </div>
 
   <div class="param">
     <label for="predictionType">Prediction Type:</label> 
     <select id="predictionType" name="predictionType" 
     	title="Specifies whether or not to show prediction accuracy for 
     	predictions that were affected by a layover. Select 'All' to show
     	data for predictions, 'Affected by layover' to only see data where
     	predictions affected by when a driver is scheduled to leave a layover, 
     	or 'Not affected by layover' if you only want data for predictions 
     	that were not affected by layovers.">
       <option value="">All</option>
       <option value="AffectedByWaitStop">Affected by layover</option>
       <option value="NotAffectedByWaitStop">Not affected by layover</option>
     </select>
   </div>
 
    <input id="submit" type="submit" value="Run Report" />
  </form>
</div>

</body>
</html>