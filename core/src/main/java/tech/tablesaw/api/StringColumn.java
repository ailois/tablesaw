/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.tablesaw.api;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import tech.tablesaw.columns.AbstractColumn;
import tech.tablesaw.columns.AbstractParser;
import tech.tablesaw.columns.Column;
import tech.tablesaw.columns.strings.ByteDictionaryMap;
import tech.tablesaw.columns.strings.DictionaryMap;
import tech.tablesaw.columns.strings.NoKeysAvailableException;
import tech.tablesaw.columns.strings.StringColumnFormatter;
import tech.tablesaw.columns.strings.StringColumnType;
import tech.tablesaw.columns.strings.StringFilters;
import tech.tablesaw.columns.strings.StringMapFunctions;
import tech.tablesaw.columns.strings.StringReduceUtils;
import tech.tablesaw.selection.BitmapBackedSelection;
import tech.tablesaw.selection.Selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static tech.tablesaw.api.ColumnType.STRING;

/**
 * A column that contains String values. They are assumed to be 'categorical' rather than free-form text, so are
 * stored in an encoding that takes advantage of the expected repetition of string values.
 * <p>
 * Because the MISSING_VALUE for this column type is an empty string, there is little or no need for special handling
 * of missing values in this class's methods.
 */
public class StringColumn extends AbstractColumn<String>
        implements CategoricalColumn<String>, StringFilters, StringMapFunctions, StringReduceUtils {

    // a bidirectional map of keys to backing string values.
    private DictionaryMap lookupTable = new ByteDictionaryMap();

    private StringColumnFormatter printFormatter = new StringColumnFormatter();

    private final IntComparator rowComparator = (i, i1) -> {
        String f1 = get(i);
        String f2 = get(i1);
        return f1.compareTo(f2);
    };

    public static boolean valueIsMissing(String string) {
        return StringColumnType.missingValueIndicator().equals(string);
    }

    @Override
    public StringColumn appendMissing() {
        lookupTable.appendMissing();
        return this;
    }

    public static StringColumn create(String name) {
        return new StringColumn(name);
    }

    public static StringColumn create(String name, String[] strings) {
        return new StringColumn(name, strings);
    }

    public static StringColumn create(String name, List<String> strings) {
        return new StringColumn(name, strings);
    }

    public static StringColumn create(String name, int size) {
        StringColumn column = new StringColumn(name, new ArrayList<>(size));
        for (int i = 0; i < size; i++) {
            column.appendMissing();
        }
        return column;
    }

    private StringColumn(String name, List<String> strings) {
        super(STRING, name);
        for (String string : strings) {
            append(string);
        }
    }

    private StringColumn(String name) {
        super(STRING, name);
    }

    private StringColumn(String name, String[] strings) {
        super(STRING, name);
        for (String string : strings) {
            append(string);
        }
    }

    @Override
    public boolean isMissing(int rowNumber) {
        return lookupTable.isMissing(rowNumber);
    }

    public void setPrintFormatter(StringColumnFormatter formatter) {
        Preconditions.checkNotNull(formatter);
        this.printFormatter = formatter;
    }

    public StringColumnFormatter getPrintFormatter() {
        return printFormatter;
    }

    @Override
    public String getString(int row) {
        return printFormatter.format(get(row));
    }

    @Override
    public String getUnformattedString(int row) {
        return String.valueOf(get(row));
    }

    @Override
    public StringColumn emptyCopy() {
        return create(name());
    }

    @Override
    public StringColumn emptyCopy(int rowSize) {
        return create(name(), rowSize);
    }

    @Override
    public void sortAscending() {
        lookupTable.sortAscending();
    }

    @Override
    public void sortDescending() {
        lookupTable.sortDescending();
    }

    /**
     * Returns the number of elements (a.k.a. rows or cells) in the column
     *
     * @return size as int
     */
    @Override
    public int size() {
        return lookupTable.size();
    }

    /**
     * Returns the value at rowIndex in this column. The index is zero-based.
     *
     * @param rowIndex index of the row
     * @return value as String
     * @throws IndexOutOfBoundsException if the given rowIndex is not in the column
     */
    public String get(int rowIndex) {
        return lookupTable.getValueForIndex(rowIndex);
    }

    /**
     * Returns a List&lt;String&gt; representation of all the values in this column
     * <p>
     * NOTE: Unless you really need a string consider using the column itself for large datasets as it uses much less memory
     *
     * @return values as a list of String.
     */
    public List<String> asList() {
        List<String> strings = new ArrayList<>();
        for (String category : this) {
            strings.add(category);
        }
        return strings;
    }

    @Override
    public Table summary() {
        return countByCategory();
    }

    /**
     */
    @Override
    public Table countByCategory() {
        return lookupTable.countByCategory(name());
    }

    @Override
    public void clear() {
        lookupTable.clear();
    }

    public StringColumn lead(int n) {
        StringColumn column = lag(-n);
        column.setName(name() + " lead(" + n + ")");
        return column;
    }

    public StringColumn lag(int n) {

        StringColumn copy = emptyCopy();
        copy.setName(name() + " lag(" + n + ")");

        if (n >= 0) {
            for (int m = 0; m < n; m++) {
                copy.appendMissing();
            }
            for (int i = 0; i < size(); i++) {
                if (i + n >= size()) {
                    break;
                }
                copy.appendCell(get(i));
            }
        } else {
            for (int i = -n; i < size(); i++) {
                copy.appendCell(get(i));
            }
            for (int m = 0; m > n; m--) {
                copy.appendMissing();
            }
        }

        return copy;
    }

    /**
     * Conditionally update this column, replacing current values with newValue for all rows where the current value
     * matches the selection criteria
     * <p>
     * Examples:
     * myCatColumn.set(myCatColumn.isEqualTo("Cat"), "Dog"); // no more cats
     * myCatColumn.set(myCatColumn.valueIsMissing(), "Fox"); // no more missing values
     */
    public StringColumn set(Selection rowSelection, String newValue) {
        for (int row : rowSelection) {
            set(row, newValue);
        }
        return this;
    }

    public StringColumn set(int rowIndex, String stringValue) {
        try {
            lookupTable.set(rowIndex, stringValue);
        } catch (NoKeysAvailableException ex) {
            lookupTable = lookupTable.promoteYourself();
            try {
                lookupTable.set(rowIndex, stringValue);
            } catch (NoKeysAvailableException e) {
                // this can't happen
                throw new RuntimeException(e);
            }
        }
        return this;
    }
    
    @Override
    public int countUnique() {
        return lookupTable.size();
    }

    /**
     * Returns the largest ("top") n values in the column
     *
     * @param n The maximum number of records to return. The actual number will be smaller if n is greater than the
     *          number of observations in the column
     * @return A list, possibly empty, of the largest observations
     */
    public List<String> top(int n) {
        List<String> top = new ArrayList<>();
        StringColumn copy = this.copy();
        copy.sortDescending();
        for (int i = 0; i < n; i++) {
            top.add(copy.get(i));
        }
        return top;
    }

    /**
     * Returns the smallest ("bottom") n values in the column
     *
     * @param n The maximum number of records to return. The actual number will be smaller if n is greater than the
     *          number of observations in the column
     * @return A list, possibly empty, of the smallest n observations
     */
    public List<String> bottom(int n) {
        List<String> bottom = new ArrayList<>();
        StringColumn copy = this.copy();
        copy.sortAscending();
        for (int i = 0; i < n; i++) {
            bottom.add(copy.get(i));
        }
        return bottom;
    }

    /**
     * Returns true if this column contains a cell with the given string, and false otherwise
     *
     * @param aString the value to look for
     * @return true if contains, false otherwise
     */
    public boolean contains(String aString) {
        return firstIndexOf(aString) >= 0;
    }

    @Override
    public Column<String> setMissing(int i) {
        return set(i, StringColumnType.missingValueIndicator());
    }

    /**
     * Add all the strings in the list to this column
     *
     * @param stringValues a list of values
     */
    public StringColumn addAll(List<String> stringValues) {
        for (String stringValue : stringValues) {
            append(stringValue);
        }
        return this;
    }

    @Override
    public StringColumn appendCell(String object) {
        return appendCell(object, StringColumnType.DEFAULT_PARSER);
    }

    @Override
    public StringColumn appendCell(String object, AbstractParser<?> parser) {
        return appendObj(parser.parse(object));
    }

    @Override
    public IntComparator rowComparator() {
        return rowComparator;
    }

    @Override
    public boolean isEmpty() {
        return lookupTable.size() == 0;
    }


    @Override
    public Selection isEqualTo(String string) {
        return lookupTable.isEqualTo(string);
    }

    @Override
    public Selection isNotEqualTo(String string) {
        return lookupTable.isNotEqualTo(string);
    }

    /**
     * Returns a list of boolean columns suitable for use as dummy variables in, for example, regression analysis,
     * select a column of categorical data must be encoded as a list of columns, such that each column represents
     * a single category and indicates whether it is present (1) or not present (0)
     *
     * @return a list of {@link BooleanColumn}
     */
    public List<BooleanColumn> getDummies() {
        return lookupTable.getDummies();
    }

    /**
     * Returns a new Column containing all the unique values in this column
     *
     * @return a column with unique values.
     */
    @Override
    public StringColumn unique() {
        List<String> strings = new ArrayList<>(lookupTable.asSet());
        return StringColumn.create(name() + " Unique values", strings);
    }

    /**
     * Returns the integers that back this column.
     *
     * @return data as {@link IntArrayList}
     */
    public IntArrayList data() {
        return lookupTable.dataAsIntArray();
    }

    public IntColumn asNumberColumn() {
        IntColumn numberColumn = IntColumn.create(this.name() + ": codes", size());
        IntArrayList data = data();
        for (int i = 0; i < size(); i++) {
            numberColumn.append(data.getInt(i));
        }
        return numberColumn;
    }

    public StringColumn where(Selection selection) {
        return subset(selection.toArray());
    }

    @Override
    public StringColumn copy() {
        StringColumn newCol = create(name(), size());
        int r = 0;
        for (String string : this) {
            newCol.set(r, string);
            r++;
        }
        return newCol;
    }

    @Override
    public StringColumn append(Column<String> column) {
        Preconditions.checkArgument(column.type() == this.type());
        StringColumn source = (StringColumn) column;
        for (String string : source) {
            append(string);
        }
        return this;
    }

    @Override
    public Column<String> append(Column<String> column, int row) {
        return append(column.getUnformattedString(row));
    }

    @Override
    public Column<String> set(int row, Column<String> column, int sourceRow) {
        return set(row, column.getUnformattedString(sourceRow));
    }

    /**
     * Returns the count of missing values in this column
     */
    @Override
    public int countMissing() {
        return lookupTable.countMissing();
    }

    @Override
    public StringColumn removeMissing() {
        StringColumn noMissing = emptyCopy();
        for (String v : this) {
            if (valueIsMissing(v)) {
                noMissing.append(v);
            }
        }
        return noMissing;
    }

    @Override
    public Iterator<String> iterator() {
        return lookupTable.iterator();
    }

    public Set<String> asSet() {
        return lookupTable.asSet();
    }

    @Override
    public int byteSize() {
        return type().byteSize();
    }

    /**
     * Returns the contents of the cell at rowNumber as a byte[]
     */
    @Override
    public byte[] asBytes(int rowNumber) {
        return lookupTable.asBytes(rowNumber);
    }

    public double getDouble(int i) {
        return lookupTable.getKeyForIndex(i);
    }

    public double[] asDoubleArray() {
        double[] doubles = new double[data().size()];
        for (int i = 0; i < size(); i++) {
            doubles[i] = data().getInt(i);
        }
        return doubles;
    }

    /**
     * Added for naming consistency with all other columns
     */
    public StringColumn append(String value) {
        try {
            lookupTable.append(value);
        } catch (NoKeysAvailableException ex) {
            lookupTable = lookupTable.promoteYourself();
            try {
                lookupTable.append(value);
            } catch (NoKeysAvailableException e) {
                // this can't happen
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    @Override
    public StringColumn appendObj(Object obj) {
        if (obj == null) {
            return appendMissing();
        }
        if (!(obj instanceof String)) {
            throw new IllegalArgumentException("Cannot append " + obj.getClass().getName() + " to StringColumn");
        }
        return append((String) obj);
    }

    @Override
    public Selection isIn(String... strings) {
        return lookupTable.selectIsIn(strings);
    }

    @Override
    public Selection isIn(Collection<String> strings) {
        return lookupTable.selectIsIn(strings);
    }

    @Override
    public Selection isNotIn(String... strings) {
        Selection results = new BitmapBackedSelection();
        results.addRange(0, size());
        results.andNot(isIn(strings));
        return results;
    }

    @Override
    public Selection isNotIn(Collection<String> strings) {
        Selection results = new BitmapBackedSelection();
        results.addRange(0, size());
        results.andNot(isIn(strings));
        return results;
    }

    public int firstIndexOf(String value) {
        return lookupTable.firstIndexOf(value);
    }

    public int countOccurrences(String value) {
        return lookupTable.countOccurrences(value);
    }

    @Override
    public Object[] asObjectArray() {
        return lookupTable.asObjectArray();
    }

    @Override
    public int compare(String o1, String o2) {
        return o1.compareTo(o2);
    }

    @Override
    public StringColumn setName(String name) {
        return (StringColumn) super.setName(name);
    }

    @Override
    public StringColumn filter(Predicate<? super String> test) {
        return (StringColumn) super.filter(test);
    }

    @Override
    public StringColumn subset(int[] rows) {
        return (StringColumn) super.subset(rows);
    }

    @Override
    public StringColumn sorted(Comparator<? super String> comp) {
        return (StringColumn) super.sorted(comp);
    }

    @Override
    public StringColumn map(Function<? super String, ? extends String> fun) {
        return (StringColumn) super.map(fun);
    }

    @Override
    public StringColumn min(Column<String> other) {
        return (StringColumn) super.min(other);
    }

    @Override
    public StringColumn max(Column<String> other) {
        return (StringColumn) super.max(other);
    }

    @Override
    public StringColumn set(Selection condition, Column<String> other) {
        return (StringColumn) super.set(condition, other);
    }

    @Override
    public StringColumn first(int numRows) {
        return (StringColumn) super.first(numRows);
    }

    @Override
    public StringColumn last(int numRows) {
        return (StringColumn) super.last(numRows);
    }

    @Override
    public StringColumn inRange(int start, int end) {
        return (StringColumn) super.inRange(start, end);
    }

    @Override
    public StringColumn sampleN(int n) {
        return (StringColumn) super.sampleN(n);
    }

    @Override
    public StringColumn sampleX(double proportion) {
        return (StringColumn) super.sampleX(proportion);
    }

    public TextColumn asTextColumn() {
        TextColumn textColumn = TextColumn.create(name(), size());
        for (int i = 0; i < size(); i++) {
            textColumn.set(i, get(i));
        }
        return textColumn;
    }
}
