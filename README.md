# hedgehog
JPA based API suite to offer complete CRUD capability with complete search capability via minimal code.

## Setup Example

This project is set up to allow you to use basic JPA code with a basic Spring Web controller. To use it, you should create a JPA entity for your table as you would normally for JPA. Example:

```
@Entity
@Table(name = "MY_DATABASE_TABLE")
public class MyModel {

    @Id
    @Column(name = "my_table_id_column")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "string_column_name")
    private String stringColumnName;

    @Column(name = "date_column_name")
    private Date dateColumnName;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getStringColumnName() {
        return stringColumnName;
    }

    public void setStringColumnName(String stringColumnName) {
        this.stringColumnName = stringColumnName;
    }

    public Date getDateTableColumnName() {
        return dateColumnName;
    }

    public void setDateTableColumnName(Date dateColumnName) {
        this.dateColumnName = dateColumnName;
    }
}
```

Once you have your model, you can create your JPA repository using the model object and the class of the primary key. In our example, the object is a class of type MyModel and the primary key is of time Integer:

```
public interface MyModelRepository extends BaseJpaRepository<MyModel, Integer> {
}
```

With the model and repositories in place, you can then build your service. It should take the same model object and primary key object as the repository. The service can then be used to expose the database functionality you wish to offer. For all CRUD functionality with no modification, it would look like the below:

```
@Service
public class MyModelService extends RestfulService<MyModel, Integer> {

    public MyModelService(MyModelRepository myModelRepository) {
        super(myModelRepository);
    }

    public List<MyModel> getMyModels(Map<String, String[]> parameters) {
        return this.getObjects(parameters, "id");
    }

    public MyModel getMyModel(Integer id) {
        return this.getObject(id);
    }

    public MyModel saveMyModel(MyModel myModel) {
        return this.saveObject(myModel);
    }

    public void deleteMyModel(Integer id) {
        this.deleteObject(id);
    }
}
```

Finally, to use in a controller to expose via an API endpoint, you simply set up your controller like the following:

```
@RestController
@RequestMapping("/api")
public class MyModelController {

    private MyModelService myModelService;

    public MyModelController(MyModelService myModelService) {
        return this.myModelService = myModelService;
    }

    @GetMapping("my-models")
    public List<MyModel> getMyModels(HttpServletRequest request) {
        return this.myModelService.getMyModels(request.getParameterMap());
    }

    @GetMapping("my-models/{id}")
    public MyModel getMyModels(@PathParam("id") Integer id) {
        return this.myModelService.getMyModel(id);
    }

    @PostMapping("my-models")
    public MyModel saveMyModel(@RequestBody MyModel myModel) {
        if (myModel.getId() != null) {
            throw new BadRequestException("My Model already exists - use PUT to update.");
        }
        return this.myModelService.saveMyModel(myModel);
    }

    @PutMapping("my-models")
    public MyModel updateMyModel(@RequestBody MyModel myModel) {
        if (myModel.getId() == null) {
            throw new BadRequestException("No ID supplied - use POST to create new");
        }
        return myModel = this.myModelService.saveMyModel(myModel);
    }

    @DeleteMapping("my-models/{id}")
    public void deleteMyModel(@PathParam("id") Integer id) {
        this.myModelService.deleteMyModel(id);
    }
}
```

## Usage

Projects set up with the basic above example offer the below functionality. Any of this can of course be overridden within the service, repository, or controller layers as needed.

### Create

To create a new MyModel object, you would need to POST to `/my-models` the following JSON - note that IDs are **excluded** in this form as this is a "create" endpoint and "update" is handled separately:

```
MyModel {
    "stringColumnName" : "Interesting String",
    "dateColumnName" : "2019-09-01T14:22:02Z"
}
```

### Update

To update a MyModel object, you would need to PUT to `/my-models` the following JSON - note that IDs are **required** in this form as this is a "update" endpoint and "create" is handled separately:

```
MyModel {
    "id" : 1
    "stringColumnName" : "More Interesting String",
    "dateColumnName" : "2019-09-01T14:22:02Z"
}
```

### Delete

To delete a MyModel object, you would need to send an HTTP DELETE to `/my-models/{id}`. In keeping with our example above, you would use: `/my-models/1`.

### Read

#### Single Resource

To read a specific MyModel object, you would submit a GET request to `/my-models/{id}`. In keeping with our example above, the url would be: `/my-models/1`.

#### Multiple Resources

This is where this project was really meant to help. With this project, you are able to perform sorting, paging, filtering, and including.

##### Sorting
All lists are automatically sorted by IDs in ascending order by default. To specify a different sort, in the query string, you need to specify the column on which you wish to sort and the direction. The direction is indicated with the `-` for descending or `+` for ascending. If no sign is indicated, `+` is used as the default. Sorting is possible on any column within the table. Using our example, to sort by stringColumnName in descending order, you would submit the following GET request:
```
/my-models?sort=-stringColumnName
```

##### Paging
Paging is broke into two distinct pieces, `start` and `count`.

`count` can be used on its own and providing the `count` in the query string will return X number of records as provided in the value. If we wished to return the first 10 MyModel objects, we would GET the following:
```
/my-models?count=10
```

The counterpart to `count` is `start` which indicates on which "page" you would like to start. For example, if we know we have 100 records in the MyModels table and we wish to return records 11 through 20, we would send a GET request to the following:
```
/my-models?count=10&start=2
```
This query string has let the backend know we wanted to get pages of size 10 and we wanted to start with the second set of 10 objects.

Paging can be used in conjunction with sort. In order to bring back MyModels third set of 10 objects sorted by ascending dateColumnName, you would use:
```
/my-models?count=10&start=3&sort=dateColumnName
```

##### Searching / Filtering
You can search/filter on any column through a variety of common criteria. With "equals" being the default, all other parameters are specified using dot-notation. Some of these supported criteria **will not work on every data type** as many do not make sense (i.e. "less than" on a "boolean", "greatest" on a "string"). Currently there is no error checking for this and it needs developed - all are still usable but the app will throw an error if an inappropriate combination is used. The current list of supported criteria is:
- equals
- not equal
- like
- starts
- ends
- less than
- greater than
- least / min
- greatest / max
- null
- not null

###### Equals
Searching by `equals` is the default. If no other parameter is specified in the query string, then it is assumed the search is to be based on strict equality. This is usable by all data types. To find all records in our MyModels table with a stringColumnName value of "Awesome", use:
```
/my-models?stringColumnName=Awesome
```

###### Not Equals
Similar to "equals" is its counterpart, "not equals". Using this parameter will find all rows in the database where the specified column's value is **not** equal to the supplied value. For example, if we wish to find all rows where stringColumnName has a value not equal to "Awesome":
```
/my-models?stringColumnName.not=Awesome
```

###### Like
Using `like` will find all rows where the specified column's value contains the value specified somewhere within it. Unlike equals, this will not search for precisely the value type but will return rows with the supplied value anywhere within it. To find all rows where the stringColumnName's value contains the word "awesome", use:
```
/my-models?stringColumnName.like=awesome
```

###### Starts
Similar to `like`, using `starts` will find all rows where the data begins with the specified value. To find all rows where stringColumnName begins with "awe":
```
/my-models?stringColumnName.starts=awe
```

###### Ends
Similar to `like`, using `ends` will find all rows where the data ends with the specified value. To find all rows where stringColumnName ends with "some":
```
/my-models?stringColumnName.ends=some
```

##### Less Than
Less Than will return all rows where the specified value is less than the value supplied. If you wished to find all rows where the dateColumnName is less than (before) January 1, 2000, you would request:
```
/my-models?dateColumnName.less=01-01-2000
```

##### Greater Than
Greater Than will return all rows where the specified value is greater than the value supplied. If you wished to find all rows where the dateColumnName is greater than (after) January 1, 2000, you would request:
```
/my-models?dateColumnName.greater=01-01-2000
```

##### Least
Using `least` will return all rows where the value in that column is the least among all the rows in the table. The keyword "min" is an alternate form of "least" and the two can be used interchangeably here. It is important to note that this does not return a single resource but rather all resources that have this value. Currently, only one column can be used at a time for the "least" function - trying to search for the least of multiple columns will throw an exception. As when specifying "least" one does not know the value within the column you wish to search, the syntax is different as the query key is the search function and the value is the column name you wish to use. For example, to find the resources with the least dateColumnName value:
```
/my-models?least=dateColumnName
```

##### Greatest
Using `greatest` will return all rows where the value in that column is the greatest among all the rows in the table. The keyword "max" is an alternate form of "greatest" and the two can be used interchangeably here. It is important to note that this does not return a single resource but rather all resources that have this value. Currently, only one column can be used at a time for the "greatest" function - trying to search for the least of multiple columns will throw an exception. As when specifying "greatest" one does not know the value within the column you wish to search, the syntax is different as the query key is the search function and the value is the column name you wish to use. For example, to find the resources with the greatest dateColumnName value:
```
/my-models?greatest=dateColumnName
```

##### Null or Not Null
To find all resources where a value is present within a column or where a value is missing in a column, you would use the `null` operator. In this case, the value within the query string is a boolean indicating whether you wish to search for null values - "true" indicating you wish to search for resources with nulls and "false" indicating you wish to find those without nulls. For example, to find all resources where stringColumnName is null, use:
```
/my-models?stringColumnName.null=true
```
Similarly, to find all resources where the stringColumnName is not null, use:
```
/my-models?stringColumnName.null=false
```

##### Combinations
All of these search criteria can be combined within a single query string in any combination of columns and/or criteria.

If you were to search for two of the *same criteria* on the *same column* (i.e. stringColumnName equals "Awesome", stringColumnName equals "Opossum"), these are combined as though they were using the "OR" operator (i.e. stringColumnName equals "Awesome" **or** "Opossum"). Example:
```
/my-models?stringColumnName=Awesome&stringColumnName=Opossum
```

If you were to search using the *same criteria* for *different columns* (i.e. stringColumnName equals "Awesome", dateColumnName equals "01-01-2000"), these would be combined as though they were using the "AND" operator (i.e. stringColumnName equals "Awesome" **and** dateColumnName equals "01-01-2000"). Example:
```
/my-models?stringColumnName=Awesome&dateColumnName="01-01-2000"
```

If you were to search using *different criteria* for the *same column* (i.e. dateColumnName greater than "01-01-2000", dateColumnName less than "01-01-2001"), these would be combined as though they were using the "AND" operator (i.e. dateColumnName greater than "01-01-2000" **and** dateColumnName less than "01-01-2001"). This is what provides one with the "between" operator as with our example you would be finding all dates that are between January 1, 2000 and January 1, 2001. Example query string:
```
/my-models?dateColumnName.greater="01-01-2000&dateColumnName.less="01-01-2001"
```

##### Putting it all together
All of the above can be used together in any combination. If you wanted to find the 3rd set of 25 resources where stringColumnName contains "awesome" or stringColumnName contains "fantastic" and the dateColumnName is greater than January 1, 2000 with the latest dateColumnName values first, the query string would be:
```
/my-models?stringColumnName.like=awesome&stringColumnName.like=fantastic&dateColumnName.greater=01-01-2000&sort=-dateColumnName&count=25&start=3
```

## Known Issues / Opportunities for Improvement
- Some of the supported criteria will not work on every data type as many do not make sense (i.e. "less than" on a "boolean", "greatest" on a "string") and there is no error checking for this
- An entity "ID" field **must** be specified currently but we should be able to gather this from the @id annotation
- Using both a "least" and "greatest" parameter at the same time will produce unknown results
- Unable to use 2 "least" or "greatest" parameters at once and no error is thrown (will return an empty result set)
- Does not support "includes" parameters to gather additional data related to the table being queried (i.e. when searching for the "customer" table, include the "address" table data related to those customers being returned)
- Not all data types supported as columns
- Passing in a column key that does not exist in the object will return an error - the key should be ignored if unknown