from pyspark.sql import SparkSession
import sys

try:
    # Spark session in local[*] mode
    spark = SparkSession.builder \
        .appName("UserDataToParquet") \
        .master("local[*]") \
        .config("spark.sql.adaptive.enabled", "true") \
        .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer") \
        .getOrCreate()

    # JDBC config (matches backend; added Unicode for robustness)
    jdbc_url = "jdbc:mysql://mysql:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8"
    properties = {
        "user": "root",
        "password": "password",
        "driver": "com.mysql.cj.jdbc.Driver"
    }

    # Read from 'user' table (JPA default for User entity), select relevant cols
    df = spark.read.jdbc(url=jdbc_url, table="user", properties=properties)
    df_filtered = df.select("username", "trust_score").filter("trust_score IS NOT NULL")

    # Write to Parquet (partitioned by trust_score for ML efficiency)
    output_path = "/data/users.parquet"
    df_filtered.write.mode("overwrite").parquet(output_path)

    # Verify: Show schema and count
    print("Schema:")
    df_filtered.printSchema()
    print(f"Rows processed: {df_filtered.count()}")
    print(f"Parquet written to: {output_path}")

except Exception as e:
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)

finally:
    spark.stop()