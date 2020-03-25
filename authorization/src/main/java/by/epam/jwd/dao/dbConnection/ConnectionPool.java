package by.epam.jwd.dao.dbConnection;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public final class ConnectionPool {
	private String driverName;
	private String url;
	private String user;
	private String password;
	private int poolSize;

	private BlockingQueue<Connection> connectionQueue;
	private BlockingQueue<Connection> givenAwayConQueue;
    private volatile static ConnectionPool instance;
    private static final Logger LOGGER = LogManager.getLogger();
    
	private ConnectionPool(){
		DBResourceManager dbResourceManager = DBResourceManager.getInstance();
		this.driverName = dbResourceManager.getValue(DBParameter.DB_DRIVER);
		this.url = dbResourceManager.getValue(DBParameter.DB_URL);
		this.user = dbResourceManager.getValue(DBParameter.DB_USER);
		this.password = dbResourceManager.getValue(DBParameter.DB_PASSWORD);

		try {
			this.poolSize = Integer.parseInt(dbResourceManager.getValue(DBParameter.DB_POOL_SIZE));
		} catch (NumberFormatException e) {
			poolSize = 5;
		}
	}

	 public static ConnectionPool getInstance() {
	        if (instance == null) {
	            synchronized (ConnectionPool.class) {
	                if (instance == null) {
	                    instance = new ConnectionPool();
	                }
	            }
	        }
	        return instance;
	    }

	public void initPool() throws ConnectionPoolException {
        LOGGER.debug("init PoolConnection start");
		try {
			Class.forName(driverName);
			connectionQueue = new ArrayBlockingQueue<>(poolSize);
			givenAwayConQueue = new ArrayBlockingQueue<>(poolSize);

			for (int i = 0; i < poolSize; i++) {
				Connection connection = DriverManager.getConnection(url, user, password);
				PooledConnection pooledConnection = new PooledConnection(connection);
				connectionQueue.add(pooledConnection);
			}
		} catch (SQLException e) {
            LOGGER.error("Connection initialization SQLException");
			throw new ConnectionPoolException("SQLException in ConnectionPool", e);
		} catch (ClassNotFoundException e) {
            LOGGER.error("Connection initialization ClassNotFoundException");
			throw new ConnectionPoolException("Can't find database driver class", e);
		}
        LOGGER.debug("init PoolConnection finish");
	}

	public void dispose() throws ConnectionPoolException {
		clearConnectionQueue();
	}

	private void clearConnectionQueue() throws ConnectionPoolException {
		try {
			closeConnectionQueue(connectionQueue);
			closeConnectionQueue(givenAwayConQueue);

		} catch (SQLException e) {
			LOGGER.log(Level.ERROR, "Error closing the connection.", e);
		}
	}

	public Connection takeConnection() throws ConnectionPoolException {
        LOGGER.debug("takeConnection start");

		Connection connection = null;
		try {
			connection = connectionQueue.take();
			givenAwayConQueue.add(connection);
		} catch (InterruptedException e) {
			throw new ConnectionPoolException("Error connecting to the data source.", e);
		}
        LOGGER.debug("takeConnection finish");
		return connection;
		}

	private void closeConnectionQueue(BlockingQueue<Connection> queue) throws SQLException {
		Connection connection = null;

		while ((connection = queue.poll()) != null) {
			if (!connection.getAutoCommit()) {
				connection.commit();
			}
			((PooledConnection)connection).reallyClose();
		}
	}
	
	public void closeConnection(Connection con, Statement st, ResultSet rs) {
        try {
            con.close();
        } catch (SQLException e) {
            LOGGER.log(Level.ERROR, "Connection isn't return to the pool", e);
        }
        try {
            rs.close();
        } catch (SQLException e) {
            LOGGER.log(Level.ERROR, "ResultSet isn't closed.", e);
        }
        try {
            st.close();
        } catch (SQLException e) {
            LOGGER.log(Level.ERROR, "Statement isn't closed.", e);
        }
    }

    public void closeConnection(Connection con, Statement st) {
        try {
            con.close();
        } catch (SQLException e) {
            LOGGER.log(Level.ERROR, "Connection isn't return to the pool", e);
        }
        try {
            st.close();
        } catch (SQLException e) {
            LOGGER.log(Level.ERROR, "Statement isn't closed.", e);
        }
    }

  private class PooledConnection implements Connection {
      private Connection connection;

      public PooledConnection(Connection c) throws SQLException {
          this.connection = c;
          this.connection.setAutoCommit(true);
      }

      public void reallyClose() throws SQLException {
          connection.close();
      }

      public void clearWarnings() throws SQLException {
          connection.close();
      }

      public void close() throws SQLException {
          if (connection.isClosed()) {
              throw new SQLException("Attempting to close closed connection");
          }
          if (connection.isReadOnly()) {
              connection.setReadOnly(false);
          }
          if (!givenAwayConQueue.remove(this)) {
              throw new SQLException("Error deleting connection from the given away connection pool.");
          }
          if (!connectionQueue.offer(this)) {
              throw new SQLException("Error allocating connection in the pool.");
          }
      }

      public void commit() throws SQLException {
          connection.commit();
      }

      public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
          return connection.createArrayOf(typeName, elements);
      }

      public Clob createClob() throws SQLException {
          return connection.createClob();
      }

      public Blob createBlob() throws SQLException {
          return connection.createBlob();
      }

      public NClob createNClob() throws SQLException {
          return connection.createNClob();
      }

      public SQLXML createSQLXML() throws SQLException {
          return connection.createSQLXML();
      }

      public Statement createStatement() throws SQLException {
          return connection.createStatement();
      }

      public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
          return connection.createStatement(resultSetType, resultSetConcurrency);
      }

      public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
          return connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
      }

      public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
          return connection.createStruct(typeName, attributes);
      }

      public PreparedStatement prepareStatement(String sql) throws SQLException {
          return connection.prepareStatement(sql);
      }

      public CallableStatement prepareCall(String sql) throws SQLException {
          return connection.prepareCall(sql);
      }

      public String nativeSQL(String sql) throws SQLException {
          return connection.nativeSQL(sql);
      }

      public void setAutoCommit(boolean autoCommit) throws SQLException {
          connection.setAutoCommit(autoCommit);
      }

      public boolean getAutoCommit() throws SQLException {
          return connection.getAutoCommit();
      }

      public void rollback() throws SQLException {
          connection.rollback();
      }

      public boolean isClosed() throws SQLException {
          return connection.isClosed();
      }

      public DatabaseMetaData getMetaData() throws SQLException {
          return connection.getMetaData();
      }

      public void setReadOnly(boolean readOnly) throws SQLException {
          connection.setReadOnly(readOnly);
      }

      public boolean isReadOnly() throws SQLException {
          return connection.isReadOnly();
      }

      public void setCatalog(String catalog) throws SQLException {
          connection.setCatalog(catalog);
      }

      public String getCatalog() throws SQLException {
          return connection.getCatalog();
      }

      public void setTransactionIsolation(int level) throws SQLException {
          connection.setTransactionIsolation(level);
      }

      public int getTransactionIsolation() throws SQLException {
          return connection.getTransactionIsolation();
      }

      public SQLWarning getWarnings() throws SQLException {
          return connection.getWarnings();
      }

      public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
          return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
      }

      public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
          return connection.prepareCall(sql, resultSetType, resultSetConcurrency);
      }

      public Map<String, Class<?>> getTypeMap() throws SQLException {
          return connection.getTypeMap();
      }

      public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
          connection.setTypeMap(map);
      }

      public void setHoldability(int holdability) throws SQLException {
          connection.setHoldability(holdability);
      }

      public int getHoldability() throws SQLException {
          return connection.getHoldability();
      }

      public Savepoint setSavepoint() throws SQLException {
          return connection.setSavepoint();
      }

      public Savepoint setSavepoint(String name) throws SQLException {
          return connection.setSavepoint(name);
      }

      public void rollback(Savepoint savepoint) throws SQLException {
          connection.rollback(savepoint);
      }

      public void releaseSavepoint(Savepoint savepoint) throws SQLException {
          connection.releaseSavepoint(savepoint);
      }

      public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
          return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
      }

      public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
          return connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
      }

      public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
          return connection.prepareStatement(sql, autoGeneratedKeys);
      }

      public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
          return connection.prepareStatement(sql, columnIndexes);
      }

      public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
          return connection.prepareStatement(sql, columnNames);
      }

      public boolean isValid(int timeout) throws SQLException {
          return connection.isValid(timeout);
      }

      public void setClientInfo(String name, String value) throws SQLClientInfoException {
          connection.setClientInfo(name, value);
      }

      public void setClientInfo(Properties properties) throws SQLClientInfoException {
          connection.setClientInfo(properties);
      }

      public String getClientInfo(String name) throws SQLException {
          return connection.getClientInfo(name);
      }

      public Properties getClientInfo() throws SQLException {
          return connection.getClientInfo();
      }

      public void setSchema(String schema) throws SQLException {
          connection.setSchema(schema);
      }

      public String getSchema() throws SQLException {
          return connection.getSchema();
      }

      public void abort(Executor executor) throws SQLException {
          connection.abort(executor);
      }

      public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
          connection.setNetworkTimeout(executor, milliseconds);
      }

      public int getNetworkTimeout() throws SQLException {
          return connection.getNetworkTimeout();
      }

      public <T> T unwrap(Class<T> iface) throws SQLException {
          return connection.unwrap(iface);
      }

      public boolean isWrapperFor(Class<?> iface) throws SQLException {
          return connection.isWrapperFor(iface);
      }
  }
}
