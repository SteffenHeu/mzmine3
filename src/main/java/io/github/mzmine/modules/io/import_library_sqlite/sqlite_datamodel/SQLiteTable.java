/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.io.import_library_sqlite.sqlite_datamodel;

import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.sql.TDFDataTable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author https://github.com/SteffenHeu
 */
public abstract class SQLiteTable<EntryKeyType> {

  private static final Logger logger = Logger.getLogger(TDFDataTable.class.getName());
  protected final String table;
  protected final String entryHeader;
  protected final List<SQLiteDataColumn<?>> columns;
  protected final SQLiteDataColumn<EntryKeyType> keyList;

  public SQLiteTable(String table, String entryHeader) {
    this.table = table;
    this.entryHeader = entryHeader;
    columns = new ArrayList<>();
    keyList = new SQLiteDataColumn<EntryKeyType>(entryHeader);
    columns.add(keyList);
  }

  public void addColumn(@NotNull SQLiteDataColumn<?> column) {
    assert column != null;
    columns.add(column);
  }

  @Nullable
  public SQLiteDataColumn<?> getColumn(String columnName) {
    for (SQLiteDataColumn<?> column : columns) {
      if (column.coulumnName.equals(columnName)) {
        return column;
      }
    }
    return null;
  }

  protected String getColumnHeadersForQuery() {
    String headers = new String();
    for (SQLiteDataColumn col : columns) {
      headers += col.getCoulumnName() + ", ";
    }
    headers = headers.substring(0, headers.length() - 2);
    return headers;
  }

  public boolean isValid() {
    long numKeys = keyList.size();
    for(SQLiteDataColumn<?> col : columns) {
      if(numKeys != col.size()) {
        return false;
      }
    }
    return true;
  }

  public boolean executeQuery(Connection connection) {
    try {
      Statement statement = connection.createStatement();

      String headers = getColumnHeadersForQuery();
      statement.setQueryTimeout(30);
      if (headers == null || headers.isEmpty()) {
        return false;
      }

      String request = getQueryText(getColumnHeadersForQuery());
      ResultSet rs = statement.executeQuery(request);
      int types[] = new int[rs.getMetaData().getColumnCount()];
      if (types.length != columns.size()) {
        logger.info("Number of retrieved columns does not match number of queried columns.");
        return false;
      }
      for (int i = 0; i < types.length; i++) {
        types[i] = rs.getMetaData().getColumnType(i + 1);
      }

      while (rs.next()) {
        for (int i = 0; i < columns.size(); i++) {
          switch (types[i]) {
            case Types.VARCHAR:
              ((SQLiteDataColumn<String>) columns.get(i)).add(rs.getString(i + 1));
              break;
            case Types.NVARCHAR:
              ((SQLiteDataColumn<String>) columns.get(i)).add(rs.getString(i + 1));
              break;
            case Types.NCHAR:
              ((SQLiteDataColumn<String>) columns.get(i)).add(rs.getString(i + 1));
              break;
            case Types.LONGVARCHAR:
              ((SQLiteDataColumn<String>) columns.get(i)).add(rs.getString(i + 1));
              break;
            case Types.LONGNVARCHAR:
              ((SQLiteDataColumn<String>) columns.get(i)).add(rs.getString(i + 1));
              break;
            case Types.CHAR:
              ((SQLiteDataColumn<String>) columns.get(i)).add(rs.getString(i + 1));
              break;
            case Types.INTEGER:
              // Bruker stores every natural number value as INTEGER in the sql database
              // Maximum size of INTEGER in SQLite: 8 bytes (64 bits) - https://sqlite.org/datatype3.html
              // So we treat everything as long (64 bit) to be on the save side.
              // this will consume more memory, though
              // However, the .dll's methods want long as argument, anyway. Otherwise we'd have to
              // cast there
              ((SQLiteDataColumn<Long>) columns.get(i)).add(rs.getLong(i + 1));
              break;
            case Types.BIGINT:
              ((SQLiteDataColumn<Long>) columns.get(i)).add(rs.getLong(i + 1));
              break;
            case Types.TINYINT:
              ((SQLiteDataColumn<Long>) columns.get(i)).add(rs.getLong(i + 1));
              break;
            case Types.SMALLINT:
              ((SQLiteDataColumn<Long>) columns.get(i)).add(rs.getLong(i + 1));
              break;
            case Types.DOUBLE:
              ((SQLiteDataColumn<Double>) columns.get(i)).add(rs.getDouble(i + 1));
              break;
            case Types.FLOAT:
              ((SQLiteDataColumn<Double>) columns.get(i)).add(rs.getDouble(i + 1));
              break;
            case Types.REAL:
              ((SQLiteDataColumn<Double>) columns.get(i)).add(rs.getDouble(i + 1));
              break;
            default:
              logger.info("Unsupported type loaded in " + table + " " + i + " " + types[i]);
              break;
          }
        }
      }
      rs.close();
      statement.close();
//      print();
//      logger.info("Recieved " + columns.size() + " * " + keyList.getEntries().size() + " entries.");
      return true;
    } catch (SQLException throwables) {
      throwables.printStackTrace();
      return false;
    }
  }

  protected String getQueryText(String columnHeadersForQuery) {
    return "SELECT " + columnHeadersForQuery + " FROM " + table;
  }

  public void print() {
    logger.info("Printing " + table + "\t" + columns.size() + " * " + keyList.size() + " entries.");
    /*for (int i = 0; i < keyList.getEntries().size(); i++) {
      String str = i + "\t";
      for (TDFDataColumn col : columns) {
        str += col.getEntries().get(i) + "\t";
      }
      logger.info(str);
    }*/
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SQLiteTable)) {
      return false;
    }
    SQLiteTable<?> that = (SQLiteTable<?>) o;
    return table.equals(that.table) &&
        entryHeader.equals(that.entryHeader) &&
        columns.equals(that.columns) &&
        keyList.equals(that.keyList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(table, entryHeader, columns, keyList);
  }

  public int size() {
    return keyList.size();
  }
}