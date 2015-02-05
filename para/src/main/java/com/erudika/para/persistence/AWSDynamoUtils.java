/*
 * Copyright 2013-2015 Erudika. http://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.persistence;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import com.erudika.para.Para;
import com.erudika.para.utils.Config;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper utilities for connecting to AWS DynamoDB.
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public final class AWSDynamoUtils {

	private static AmazonDynamoDBClient ddbClient;
	private static final String LOCAL_ENDPOINT = "http://localhost:8000";
	private static final String ENDPOINT = "dynamodb.".concat(Config.AWS_REGION).concat(".amazonaws.com");
	private static final Logger logger = LoggerFactory.getLogger(AWSDynamoUtils.class);

	private AWSDynamoUtils() { }

	/**
	 * Returns a client instance for AWS DynamoDB
	 * @return a client that talks to DynamoDB
	 */
	public static AmazonDynamoDBClient getClient() {
		if (ddbClient != null) {
			return ddbClient;
		}

		if (Config.IN_PRODUCTION) {
			ddbClient = new AmazonDynamoDBClient();
			ddbClient.setEndpoint(ENDPOINT);
		} else {
			ddbClient = new AmazonDynamoDBClient(new BasicAWSCredentials("local", "null"));
			ddbClient.setEndpoint(LOCAL_ENDPOINT);
		}

		if (!existsTable(Config.APP_NAME_NS)) {
			createTable(Config.APP_NAME_NS);
		}

		Para.addDestroyListener(new Para.DestroyListener() {
			public void onDestroy() {
				shutdownClient();
			}
		});

		return ddbClient;
	}

	/**
	 * Stops the client and releases resources.
	 * <b>There's no need to call this explicitly!</b>
	 */
	protected static void shutdownClient() {
		if (ddbClient != null) {
			ddbClient.shutdown();
			ddbClient = null;
		}
	}

	/**
	 * Checks if the main table exists in the database.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if the table exists
	 */
	public static boolean existsTable(String appid) {
		if (StringUtils.isBlank(appid)) {
			return false;
		}
		try {
			List<String> tables = getClient().listTables().getTableNames();
			return tables != null && tables.contains(getTablNameForAppid(appid));
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Creates the main table.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if created
	 */
	public static boolean createTable(String appid) {
		return createTable(appid, 2L, 1L);
	}

	/**
	 * Creates a table in AWS DynamoDB.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param readCapacity read capacity
	 * @param writeCapacity write capacity
	 * @return true if created
	 */
	public static boolean createTable(String appid, Long readCapacity, Long writeCapacity) {
		if (StringUtils.isBlank(appid) || StringUtils.containsWhitespace(appid) || existsTable(appid)) {
			return false;
		}
		try {
			getClient().createTable(new CreateTableRequest().withTableName(getTablNameForAppid(appid)).
					withKeySchema(new KeySchemaElement(Config._KEY, KeyType.HASH)).
					withAttributeDefinitions(new AttributeDefinition().withAttributeName(Config._KEY).
					withAttributeType(ScalarAttributeType.S)).
					withProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity)));
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Updates the table settings (read and write capacities)
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @param readCapacity read capacity
	 * @param writeCapacity write capacity
	 * @return true if updated
	 */
	public static boolean updateTable(String appid, Long readCapacity, Long writeCapacity) {
		if (StringUtils.isBlank(appid) || StringUtils.containsWhitespace(appid) || existsTable(appid)) {
			return false;
		}
		try {
			getClient().updateTable(new UpdateTableRequest().withTableName(getTablNameForAppid(appid)).
					withProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity)));
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Deletes the main table from AWS DynamoDB.
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return true if deleted
	 */
	public static boolean deleteTable(String appid) {
		if (StringUtils.isBlank(appid) || !existsTable(appid)) {
			return false;
		}
		try {
			getClient().deleteTable(new DeleteTableRequest().withTableName(getTablNameForAppid(appid)));
		} catch (Exception e) {
			logger.error(null, e);
			return false;
		}
		return true;
	}

	/**
	 * Gives basic information about a DynamoDB table (status, creation date, size).
	 * @param appid name of the {@link com.erudika.para.core.App}
	 * @return a map
	 */
	public static Map<String, Object> getTableStatus(String appid) {
		if (StringUtils.isBlank(appid)) {
			return Collections.emptyMap();
		}
		try {
			Map<String, Object> map = new HashMap<String, Object>();
			final TableDescription td = getClient().describeTable(getTablNameForAppid(appid)).getTable();
			return new HashMap<String, Object>() {
				{
					put("status", td.getTableStatus());
					put("created", td.getCreationDateTime().getTime());
					put("sizeBytes", td.getTableSizeBytes());
					put("itemCount", td.getItemCount());
					put("readCapacityUnits", td.getProvisionedThroughput().getReadCapacityUnits());
					put("writeCapacityUnits", td.getProvisionedThroughput().getWriteCapacityUnits());
				}
			};
		} catch (Exception e) {
			logger.error(null, e);
		}
		return Collections.emptyMap();
	}

	protected static String getTablNameForAppid(String appIdentifier) {
		if (StringUtils.isBlank(appIdentifier)) {
			return null;
		} else {
			return appIdentifier.equals(Config.APP_NAME_NS) ? appIdentifier : Config.PARA + "-" + appIdentifier;
		}
	}
}
