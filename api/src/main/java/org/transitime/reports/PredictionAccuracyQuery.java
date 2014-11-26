/*
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */

package org.transitime.reports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitime.db.GenericQuery;
import org.transitime.statistics.Statistics;
import org.transitime.utils.Time;

/**
 * For doing SQL query and generating JSON data for a prediction accuracy
 * intervals chart.
 *
 * @author SkiBu Smith
 *
 */
public class PredictionAccuracyQuery {

    private final Connection connection;
    
    private static final int MAX_PRED_LENGTH = 900;
    private static final int PREDICTION_LENGTH_BUCKET_SIZE = 30;
    
    // If fewer than this many datapoints for a prediction bucket then 
    // stats are not provided since really can't determine standard
    // deviation or percentages for such situation.
    private static final int MIN_DATA_POINTS_PER_PRED_BUCKET = 5;
    
    // Keyed on source (so can show data for multiple sources at
    // once in order to compare prediction accuracy. Contains a array,
    // with an element for each prediction bucket, containing an array
    // of the prediction accuracy values for that bucket. Each bucket is for 
    // a certain prediction range, specified by predictionLengthBucketSize.
    private final Map<String, List<List<Integer>>> map = 
	    new HashMap<String, List<List<Integer>>>();
    
    // Defines the output type for the intervals, whether should show
    // standard deviation, percentage, or both. 
    // Can iterate over the enumerated type using:
    //  for (IntervalsType type : IntervalsType.values()) {}
    public enum IntervalsType {
	PERCENTAGE("PERCENTAGE"),
	STD_DEV("STD_DEV"),
	BOTH("BOTH");
	
	private final String text;
	
	private IntervalsType(final String text) {
	    this.text = text;
	}
	
	/**
	 * For converting from a string to an IntervalsType
	 * 
	 * @param text
	 *            String to be converted
	 * @return The corresponding IntervalsType, or IntervalsType.PERCENTAGE
	 *         as the default if text doesn't match a type.
	 */
	public static IntervalsType createIntervalsType(String text) {
	    for (IntervalsType type : IntervalsType.values()) {
		if (type.toString().equals(text)) {
		    return type;
		}
	    }
	    
	    // If a bad non-null value was specified then log the error
	    if (text != null)
		logger.error("\"{}\" is not a valid IntervalsType", text);
	    
	    // Couldn't match so use default value
	    return IntervalsType.PERCENTAGE;
	}
	
	@Override
	public String toString() {
	    return text;
	}
	
    }
    private static final Logger logger = LoggerFactory
	    .getLogger(PredictionAccuracyQuery.class);

    /********************** Member Functions **************************/

    public PredictionAccuracyQuery(String dbType, String dbHost,
	    String dbName, String dbUserName, String dbPassword)
	    throws SQLException {
	connection = GenericQuery.getConnection(dbType, dbHost,
		    dbName, dbUserName, dbPassword);

    }
    
    /**
     * Determines which prediction bucket in the map to use. Want to have each
     * bucket to be for an easily understood value, such as 1 minute. Best way
     * to do this is then have the predictions for that bucket be 45 seconds to
     * 75 seconds so that the indicator for the bucket (1 minute) is in the
     * middle of the range.
     * 
     * @param predLength
     * @return
     */
    private static int index(int predLength) {
	return (predLength + PREDICTION_LENGTH_BUCKET_SIZE/2) / 
		PREDICTION_LENGTH_BUCKET_SIZE;
    }
    
    /**
     * Puts the data from the query into the map so it can be further processed
     * later.
     * 
     * @param predLength
     * @param predAccuracy
     * @param source
     */
    private void addDataToMap(int predLength, int predAccuracy, String source) {
	// Get the prediction buckets for the specified source
	List<List<Integer>> predictionBuckets = map.get(source);
	if (predictionBuckets == null) {
	    predictionBuckets = new ArrayList<List<Integer>>();
	    map.put(source, predictionBuckets);
	}
	
	// Determine the index of the appropriate prediction bucket
	int predictionBucketIndex = index(predLength);
	while (predictionBuckets.size() < predictionBucketIndex+1)
	    predictionBuckets.add(new ArrayList<Integer>());
	List<Integer> predictionAccuracies = 
		predictionBuckets.get(predictionBucketIndex);
	
	// Add the prediction accuracy to the bucket.
	predictionAccuracies.add(predAccuracy);
    }
    
    /**
     * Goes through data array (must already be sorted!) and determines the
     * index of the array that corresponds to the minimum element. For example,
     * if the fraction is specified as 0.70 which means that want to know the
     * minimum value such that 70% of the predictions are between the min and
     * the max, then will return the index for the item whose index is at
     * (100%-70%)/2 = 15% in the array.
     * 
     * @param data
     *            Sorted list of data
     * @param percentage
     *            The percentage (0.0 - 100.0%) of prediction accuracy data that
     *            should be between the min and the max
     * @return Value of the desired element or null if fraction not valid
     */
    private Long getMin(List<Integer> data, double percentage) {
	if (percentage == 0.0 || Double.isNaN(percentage))
	    return null;
	
	double fraction = percentage / 100.0;
	
	int index = (int) (data.size() * (1-fraction) / 2);
	return (long) data.get(index);    
    }
    
    /**
     * Goes through data array (must already be sorted!) and determines the
     * index of the array that corresponds to the maximum element. For example,
     * if the fraction is specified as 0.70 which means that want to know the
     * minimum value such that 70% of the predictions are between the min and
     * the max, then will return the index for the item whose index is at
     * 85% in the array.
     * 
     * @param data
     *            Sorted list of data
     * @param percentage
     *            The percentage (0.0 - 100.0%) of prediction accuracy data that
     *            should be between the min and the max
     * @return Value of the desired element or null if fraction not valid
     */
    private Long getMax(List<Integer> data, double percentage) {
	if (percentage == 0.0 || Double.isNaN(percentage))
	    return null;
	
	double fraction = percentage / 100.0;

	int index = (int) (data.size() * (fraction + (1-fraction) / 2));
	return (long) data.get(index);    
    }
    
    /**
     * Gets the column definition in JSON string format so that chart the data
     * using Google charts. The column definition describes the contents of each
     * column but doesn't actually contain the data itself.
     * 
     * @param intervalsType
     * @param intervalPercentage1
     * @param intervalPercentage2
     * @return The column portion of the JSON string
     */
    private String getCols(IntervalsType intervalsType,
	    double intervalPercentage1, double intervalPercentage2) {
	if (map.isEmpty())
	    return null;
	
	// Start column definition
	StringBuilder result = new StringBuilder();
	result.append("\n  \"cols\": [");

	// Column for x axis, which is prediction length bucket
	result.append("\n    {\"type\": \"number\"}");

	for (String source : map.keySet()) {
	    // The average result for the source
	    result.append(",\n    {\"type\": \"number\", \"label\":\"" + source + "\"}");
	    
	    // The first interval
	    result.append(",\n    {\"type\": \"number\", \"p\":{\"role\":\"interval\"} }");
	    result.append(",\n    {\"type\": \"number\", \"p\":{\"role\":\"interval\"} }");

	    // The second interval. But only want to output it if there actually
	    // should be a second interval. Otherwise if use nulls for the second
	    // interval Google chart doesn't draw even the first interval.
	    if (intervalsType != IntervalsType.PERCENTAGE
		    || (!Double.isNaN(intervalPercentage2) 
			    && intervalPercentage2 != 0.0)) {
		result.append(",\n    {\"type\": \"number\", \"p\":{\"role\":\"interval\"} }");
		result.append(",\n    {\"type\": \"number\", \"p\":{\"role\":\"interval\"} }");
	    }
	}
	
	// Finish up column definition
	result.append("\n  ]");
	
	// Return column definition
	return result.toString();
    }
    
    /**
     * Gets the row definition in JSON string format so that chart the data
     * using Google charts. The row definition contains the actual data.
     * 
     * @param intervalsType
     *            Specifies whether should output for intervals standard
     *            deviation info, percentage info, or both.
     * @param intervalPercentage1
     *            For when outputting intervals as fractions. Not used if
     *            intervalsType is STD_DEV.
     * @param intervalPercentage2
     *            For when outputting intervals as fractions. Only used if
     *            intervalsType is PERCENTAGE.
     * @return The row portion of the JSON string
     */
    private String getRows(IntervalsType intervalsType,
	    double intervalPercentage1, double intervalPercentage2) {
	// If something is really wrong then complain
	if (map.isEmpty()) {
	    logger.error("Called PredictionAccuracyQuery.getRows() but there "
	    	+ "is no data in the map.");
	    return null;
	}
	
	StringBuilder result = new StringBuilder();
	result.append("\n  \"rows\": [");

	// For each prediction length bucket...
	boolean firstRow = true;
	for (int predBucketIdx=0; 
		predBucketIdx<=MAX_PRED_LENGTH/PREDICTION_LENGTH_BUCKET_SIZE; 
		++predBucketIdx) {
	    // Deal with comma for end of previous row
	    if (!firstRow)
		result.append(",");
	    firstRow = false;
	    
	    double horizontalValue = 
		    predBucketIdx * PREDICTION_LENGTH_BUCKET_SIZE/60.0;
	    result.append("\n    {\"c\": [{\"v\": " + horizontalValue + "}");
	    
	    // Add prediction mean and intervals data for each source
	    for (String source : map.keySet()) {
		// Determine mean and standard deviation for this source
		List<List<Integer>> dataForSource = map.get(source);
		List<Integer> listForPredBucket = null;
		if (dataForSource != null && dataForSource.size() > predBucketIdx)
		    listForPredBucket = dataForSource.get(predBucketIdx);
		
		// Sort the prediction accuracy data so that can call
		// getMin() and getMax() using necessary sort list.
		if (listForPredBucket != null)
		    Collections.sort(listForPredBucket);

		// Log some info for debugging
		logger.info("For source {} for prediction bucket minute {} "
			+ "sorted datapoints={}", source, horizontalValue,
			listForPredBucket);
		    
		// If there is enough data then handle stats for this prediction
		// bucket. If there are fewer than 
		// MIN_DATA_POINTS_PER_PRED_BUCKET datapoints for the bucket 
		// then can determine standard deviation and the percentage 
		// based min and max intervals would not be valid either. This
		// would cause an unsightly and inappropriate necking of data
		// for this bucket. 
		if (listForPredBucket != null
			&& listForPredBucket.size() >= MIN_DATA_POINTS_PER_PRED_BUCKET) {
		    // Determine the mean
		    double dataForPredBucket[] = 
			    Statistics.toDoubleArray(listForPredBucket);
		    double mean = Statistics.mean(dataForPredBucket);
		    
		    // Determine the standard deviation and handle special case
		    // of when there is only a single data point such that the
		    // standard deviation is NaN.
		    double stdDev = Statistics.getSampleStandardDeviation(
			    dataForPredBucket, mean);
		    if (Double.isNaN(stdDev))
			stdDev = 0.0;
		    
		    // Output the mean value
		    result.append(",{\"v\": " + Math.round(mean) + "}");
		    
		    // Output the first interval values. Using int instead of 
		    // double because that is enough precision and then the 
		    // tooltip info looks lot better. 
		    Long intervalMin;
		    Long intervalMax;
		    if (intervalsType == IntervalsType.PERCENTAGE) {
			intervalMin = getMin(listForPredBucket, intervalPercentage1);
			intervalMax = getMax(listForPredBucket, intervalPercentage1);
		    } else {
			// Use single standard deviation
			intervalMin = Math.round(mean-stdDev);
		    	intervalMax = Math.round(mean+stdDev);
		    }
		    
		    result.append(",{\"v\": " + intervalMin + "}");
		    result.append(",{\"v\": " + intervalMax + "}");

		    // Output the second interval values
		    if (intervalsType == IntervalsType.PERCENTAGE) {
			// Chart can't seem to handle null values for an interval
			// so if intervalFraction2 is not set then don't put
			// out this interval info.
			if (intervalPercentage2 == 0.0 || Double.isNaN(intervalPercentage2))
				continue;
			
			intervalMin = getMin(listForPredBucket, intervalPercentage2);
			intervalMax = getMax(listForPredBucket, intervalPercentage2);			
		    } else if (intervalsType == IntervalsType.BOTH) {
			// Use percentage but since also displaying results 
			// for a single deviation use a fraction that 
			// corresponds, which is 0.68.
			intervalMin = getMin(listForPredBucket, 0.68);
			intervalMax = getMax(listForPredBucket, 0.68);
		    } else {
			// Using standard deviation for second interval. Use
			// 1.5 standard deviations, which corresponds to 86.6%
			intervalMin = Math.round(mean - 1.5*stdDev);
		    	intervalMax = Math.round(mean + 1.5*stdDev);
		    }
		    result.append(",{\"v\": " + intervalMin + "}");
		    result.append(",{\"v\": " + intervalMax + "}");
		} else {
		    // Handle situation when there isn't enough data in the 
		    // prediction bucket.
		    result.append(",{\"v\": null},{\"v\": null},{\"v\": null}");
		}
	    } // End of for each source
	    
	    // Finish up row
	    result.append("]}");
	}
	// Finish up rows section
	result.append(" \n ]");
	return result.toString();
    }
    
    /**
     * Performs the SQL query and puts the resulting data into the map.
     * 
     * @param beginDateStr
     *            Begin date for date range of data to use.
     * @param endDateStr
     *            End date for date range of data to use. Since want to include
     *            data for the end date, 1 day is added to the end date for the
     *            query.
     * @param beginTimeStr
     *            For specifying time of day between the begin and end date to
     *            use data for. Can thereby specify a date range of a week but
     *            then just look at data for particular time of day, such as 7am
     *            to 9am, for those days. Set to null or empty string to use
     *            data for entire day.
     * @param endTimeStr
     *            For specifying time of day between the begin and end date to
     *            use data for. Can thereby specify a date range of a week but
     *            then just look at data for particular time of day, such as 7am
     *            to 9am, for those days. Set to null or empty string to use
     *            data for entire day.
     * @param routeIds
     *            Array of IDs of routes to get data for
     * @param predSource
     *            The source of the predictions. Can be null or "" (for all),
     *            "Transitime", or "Other"
     * @param predType
     *            Whether predictions are affected by wait stop. Can be "" (for
     *            all), "AffectedByWaitStop", or "NotAffectedByWaitStop".
     * @throws SQLException
     * @throws ParseException 
     */
    private void doQuery(String beginDateStr, String endDateStr,
	    String beginTimeStr, String endTimeStr, String routeIds[],
	    String predSource, String predType) throws SQLException, ParseException {
	// Make sure not trying to get data for too long of a time span since
	// that could bog down the database.
	long timespan = Time.parseDate(endDateStr).getTime() - 
		Time.parseDate(beginDateStr).getTime() + 1*Time.MS_PER_DAY;
	if (timespan > 31*Time.MS_PER_DAY) {
	    throw new ParseException("Begin date to end date spans more than a month", 0);
	}

	// Determine the time of day portion of the SQL
	String timeSql = "";
	if ((beginTimeStr != null && !beginTimeStr.isEmpty())
		|| (endTimeStr != null && !endTimeStr.isEmpty())) {
	    timeSql = " AND arrivalDepartureTime::time BETWEEN ? AND ? ";
	}
	
	// Determine route portion of SQL
	String routeSql = "";
	if (routeIds != null) {
	    routeSql = " AND (routeId=?";
	    for (int i=1; i<routeIds.length; ++i)
		routeSql += " OR routeId=?"; 
	    routeSql += ")";
	}
	
	// Determine the source portion of the SQL. Default is to provide
	// predictions for all sources
	String sourceSql = "";
	if (predSource != null && !predSource.isEmpty()) {
	    if (predSource.equals("Transitime")) {
		// Only "Transitime" predictions
		sourceSql = " AND predictionSource='Transitime'";
	    } else {
		// Anything but "Transitime"
		sourceSql = " AND predictionSource<>'Transitime'";
	    }
	}
	
	// Determine SQL for prediction type. Can be "" (for
	// all), "AffectedByWaitStop", or "NotAffectedByWaitStop".
	String predTypeSql = "";
	if (predType != null && !predType.isEmpty()) {
	    if (predSource.equals("AffectedByWaitStop")) {
		// Only "AffectedByLayover" predictions
		predTypeSql = " AND affectedByWaitStop = true ";
	    } else {
		// Only "NotAffectedByLayover" predictions
		predTypeSql = " AND affectedByWaitStop = false ";
	    }
	}

	// Put the entire SQL query together
	String sql = "SELECT "
		+ "     to_char(predictedTime-predictionReadTime, 'SSSS')::integer as predLength, "
		+ "     predictionAccuracyMsecs/1000 as predAccuracy, "
		+ "     predictionSource as source "
		+ " FROM predictionAccuracy "
		+ "WHERE arrivalDepartureTime BETWEEN ? AND ? "
		+ timeSql
		+ "  AND predictedTime-predictionReadTime < '00:15:00' "
		// Filter out MBTA_seconds source since it is isn't significantly different from MBTA_epoch. 
		// TODO should clean this up by not having MBTA_seconds source at all
		// in the prediction accuracy module for MBTA.
		+ "  AND predictionSource <> 'MBTA_seconds' "
		+ routeSql
		+ sourceSql
		+ predTypeSql;

	PreparedStatement statement = null;	
	try {
	    statement = connection.prepareStatement(sql);
	    
	    // Determine the date parameters for the query
	    Timestamp beginDate = null;
	    Timestamp endDate = null;
	    java.util.Date date = Time.parseDate(beginDateStr);
	    beginDate = new Timestamp(date.getTime());

	    date = Time.parseDate(endDateStr);
	    endDate = new Timestamp(date.getTime() + Time.MS_PER_DAY);
	    
	    // Determine the time parameters for the query
	    // If begin time not set but end time is then use midnight as begin time
	    if ((beginTimeStr == null || beginTimeStr.isEmpty()) 
		    && endTimeStr != null && !endTimeStr.isEmpty()) {
		beginTimeStr = "00:00:00";
	    }
	    // If end time not set but begin time is then use midnight as end time
	    if ((endTimeStr == null || endTimeStr.isEmpty()) 
		    && beginTimeStr != null && !beginTimeStr.isEmpty()) {
		endTimeStr = "23:59:59";
	    }
	    
	    java.sql.Time beginTime = null;
	    java.sql.Time endTime = null;
	    if (beginTimeStr != null && !beginTimeStr.isEmpty()) {
		beginTime = new java.sql.Time(Time.parseTimeOfDay(beginTimeStr) * Time.MS_PER_SEC);
	    }
	    if (endTimeStr != null && !endTimeStr.isEmpty()) {
		endTime = new java.sql.Time(Time.parseTimeOfDay(endTimeStr) * Time.MS_PER_SEC);
	    }
	    
	    // Set the parameters for the query
	    int i=1;
	    statement.setTimestamp(i++, beginDate);
	    statement.setTimestamp(i++, endDate);
	    if (beginTime != null)
		statement.setTime(i++, beginTime);
	    if (endTime != null)
		statement.setTime(i++, endTime);
	    if (routeIds != null) {
		for (String routeId : routeIds)
		    statement.setString(i++, routeId);
	    }
	    
	    // Actually execute the query
	    ResultSet rs = statement.executeQuery();

	    // Process results of query
	    while (rs.next()) {
		int predLength = rs.getInt("predLength");
		int predAccuracy = rs.getInt("predAccuracy");
		String sourceResult = rs.getString("source");
		
		addDataToMap(predLength, predAccuracy, sourceResult);
		logger.info("predLength={} predAccuracy={}", 
			predLength, predAccuracy);
	    }
	} catch (SQLException e) {
	    throw e;
	} finally {
	    if (statement != null)
		statement.close();
	}	
    }

    /**
     * Performs the query and returns the data in an JSON string so that it can
     * be used for a chart.
     *
     * @param beginDateStr
     *            Begin date for date range of data to use.
     * @param endDateStr
     *            End date for date range of data to use. Since want to include
     *            data for the end date, 1 day is added to the end date for the
     *            query.
     * @param beginTimeStr
     *            For specifying time of day between the begin and end date to
     *            use data for. Can thereby specify a date range of a week but
     *            then just look at data for particular time of day, such as 7am
     *            to 9am, for those days. Set to null or empty string to use
     *            data for entire day.
     * @param endTimeStr
     *            For specifying time of day between the begin and end date to
     *            use data for. Can thereby specify a date range of a week but
     *            then just look at data for particular time of day, such as 7am
     *            to 9am, for those days. Set to null or empty string to use
     *            data for entire day.
     * @param routeIds
     *            Specifies which routes to do the query for. Can be null for
     *            all routes or an array of route IDs.
     * @param predSource
     *            The source of the predictions. Can be null or "" (for all),
     *            "Transitime", or "Other"
     * @param predType
     *            Whether predictions are affected by wait stop. Can be "" (for
     *            all), "AffectedByWaitStop", or "NotAffectedByWaitStop".
     * @param intervalsType
     *            Specifies whether should output for intervals standard
     *            deviation info, percentage info, or both.
     * @param intervalPercentage1
     *            For when outputting intervals as percentages. Not used if
     *            intervalsType is STD_DEV.
     * @param intervalPercentage2
     *            For when outputting intervals as percentages. Only used if
     *            intervalsType is PERCENTAGE.
     * @return the full JSON string contain both cols and rows info, or null if
     *         no data returned from query
     * @throws SQLException
     * @throws ParseException 
     */
    public String getJson(String beginDateStr, String endDateStr,
	    String beginTimeStr, String endTimeStr, String routeIds[],
	    String predSource, String predType, IntervalsType intervalsType,
	    double intervalPercentage1, double intervalPercentage2)
	    throws SQLException, ParseException {
	// Actually perform the query
	doQuery(beginDateStr, endDateStr, beginTimeStr, endTimeStr, routeIds,
		predSource, predType);
	
	// If query returned no data then simply return null so that
	// can easily see that there is a problem
	if (map.isEmpty()) {
	    return null;
	}
	
	return "{" 
		+ getCols(intervalsType, intervalPercentage1, intervalPercentage2) + "," 
		+ getRows(intervalsType, intervalPercentage1, intervalPercentage2) 
		+ "\n}";
    }
    
    /**
     * For debugging
     * 
     * @param args
     */
    public static void main(String args[]) {
	String beginDate = "11-03-2014";
	String endDate = "11-06-2014";
	String beginTime = "00:00:00";
	String endTime = "23:59:59";
	String routeIds[] = {"CR-Providence"};
	String source = "Transitime";
	
	String dbType = "postgresql";// "mysql";
	String dbHost = "sfmta.c3zbap9ppyby.us-west-2.rds.amazonaws.com";// "localhost";
	String dbName = "mbta";
	String dbUserName = "transitime";// "root";
	String dbPassword = "transitime";

	try {
	    PredictionAccuracyQuery query = new PredictionAccuracyQuery(dbType,
		    dbHost, dbName, dbUserName, dbPassword);
	    String jsonString = query.getJson(beginDate, endDate, beginTime,
		    endTime, routeIds, source, null, IntervalsType.BOTH, 0.68,
		    0.80);
	    System.out.println(jsonString);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
