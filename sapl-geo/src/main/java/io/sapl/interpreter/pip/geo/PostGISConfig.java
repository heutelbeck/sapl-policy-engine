package io.sapl.interpreter.pip.geo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import io.sapl.api.pip.AttributeException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class PostGISConfig {

	private static final String ARG_NOT_EXISTING = "Configured table or column name does not exist in the database.";
	private static final String COLUMN_NAME = "COLUMN_NAME";
	private static final String JDBC_SERVICE = "jdbc:postgresql://";
	private static final String SSL_PARAM = "ssl=true";
	private static final String SQL_QUERY = "SELECT %s, %s FROM %s WHERE %s;";
	private static final String SQL_PROJECTGEOM = "ST_AsText(ST_FlipCoordinates(ST_Transform(%s,%s)))";
	private static final String SQL_GEOM = "ST_AsText(ST_FlipCoordinates(%s))";
	private static final String SQL_AND = " AND ";
	private static final String SLASH = "/";
	private static final String QM = "?";
	private static final String AMP = "&";
	private static final String SEQ = "<=";
	private static final String GEQ = ">=";
	private static final char COLON = ':';

	private String serverAdress;
	private String port;
	private String db;
	private String table;
	private String username;
	private String password;
	private String pkColName;
	private String idColName;
	private String geometryColName;
	private int from;
	private int until = -1;
	private boolean enableProjection;
	private int projectionSRID;
	private boolean ssl;
	private String urlParams;

	public Connection getConnection() throws SQLException {
		return DriverManager.getConnection(buildUrl(), getUsername(), getPassword());
	}

	public String buildQuery() throws AttributeException {
		if (verifySqlArguments()) {
			return String.format(SQL_QUERY, getIdColName(), buildGeometryExpression(), getTable(), buildConditions());
		} else {
			throw new AttributeException(ARG_NOT_EXISTING);
		}
	}

	protected String buildUrl() {
		StringBuilder url = new StringBuilder();
		url.append(JDBC_SERVICE).append(getServerAdress()).append(COLON).append(getPort()).append(SLASH).append(getDb())
				.append(QM);

		if (ssl) {
			url.append(SSL_PARAM);
		}
		if (urlParams != null && urlParams.length() > 0) {
			url.append(AMP).append(urlParams);
		}

		return url.toString();
	}

	protected boolean verifySqlArguments() throws AttributeException {
		try (Connection conn = getConnection()) {

			DatabaseMetaData dbm = conn.getMetaData();
			ResultSet cols = dbm.getColumns(null, null, getTable(), null);

			if (colsExist(cols, getIdColName(), getGeometryColName(), getPkColName())) {
				return true;
			}
		} catch (SQLException e) {
			throw new AttributeException(e);
		}
		return false;
	}

	private String buildConditions() {
		StringBuilder conditions = new StringBuilder();
		conditions.append(getPkColName()).append(GEQ).append(getFrom());
		if (getUntil() >= getFrom()) {
			conditions.append(SQL_AND).append(getPkColName()).append(SEQ).append(getUntil());
		}
		return conditions.toString();
	}

	private String buildGeometryExpression() {
		if (isEnableProjection() && getProjectionSRID() != 0) {
			return String.format(SQL_PROJECTGEOM, getGeometryColName(), getProjectionSRID());
		} else {
			return String.format(SQL_GEOM, getGeometryColName());
		}
	}

	protected static boolean colsExist(ResultSet cols, String... colNames) throws SQLException {
		ArrayList<String> colNamesList = new ArrayList<>();

		while (cols.next()) {
			colNamesList.add(cols.getString(COLUMN_NAME));
		}

		for (String colName : colNames) {
			if (!colNamesList.contains(colName)) {
				return false;
			}
		}
		return true;

	}
}
