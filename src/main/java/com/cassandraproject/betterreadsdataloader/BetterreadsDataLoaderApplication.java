package com.cassandraproject.betterreadsdataloader;

import com.cassandraproject.betterreadsdataloader.author.Author;
import com.cassandraproject.betterreadsdataloader.author.AuthorRepository;
import com.cassandraproject.betterreadsdataloader.book.Book;
import com.cassandraproject.betterreadsdataloader.book.BookRepository;
import connection.DataStaxAstraProperties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterreadsDataLoaderApplication {

	@Autowired
	AuthorRepository authorRepository;

	@Autowired
	BookRepository bookRepository;

	@Value("${datadump.location.author}")
	private String authorDumpLocation;

	@Value("${datadump.location.works}")
	private String worksDumpLocation;

	public static void main(String[] args) {
		SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
	}


	@PostConstruct
	public void start() {
		// initAuthors();
		initWorks();
	}

	private void initAuthors() {
		Path path = Paths.get(authorDumpLocation);
		try (Stream<String> lines = Files.lines(path)){
			lines.forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);

					// Construct Author object
					Author author = new Author();
					author.setId(jsonObject.optString("key").replace("/authors/", ""));
					author.setName(jsonObject.optString("name"));
					author.setPersonalName(jsonObject.optString("personal_name"));

					// Persist using Repository
					authorRepository.save(author);
					
				} catch (JSONException e) {
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void initWorks() {
		Path path = Paths.get(worksDumpLocation);
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		try (Stream<String> lines = Files.lines(path)) {
			lines.limit(50).forEach(line -> {
				// Read and parse the line
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);

					// Construct Book object
					Book book = new Book();
					book.setId(jsonObject.optString("key").replace("/works/", ""));
					book.setName(jsonObject.optString("name"));
					JSONObject descriptionObj = jsonObject.optJSONObject("description");

					if (descriptionObj != null) {
						book.setDescription(descriptionObj.optString("value"));
					}

					JSONObject publishedObj = jsonObject.optJSONObject("created");

					if (publishedObj != null) {
						String dateStr = publishedObj.getString("value");
						book.setPublishedDate(LocalDate.parse(dateStr, dateFormat));
					}

					JSONArray coversJsonArray = jsonObject.optJSONArray("covers");

					if (coversJsonArray != null) {
						List<String> coverIds = new ArrayList<>();
						for (int i = 0; i < coversJsonArray.length(); i++) {
							coverIds.add(coversJsonArray.getString(i));

						}
						book.setCoverIds(coverIds);
					}

					JSONArray authorsJsonArray = jsonObject.optJSONArray("authors");

					if (authorsJsonArray != null) {
						List<String> authorIds = new ArrayList<>();
						for (int i = 0; i < authorsJsonArray.length(); i++) {
							String authorId = authorsJsonArray.getJSONObject(i).getJSONObject("author").getString("key").replace("/authors/", "");
							authorIds.add(authorId);
						}
						book.setAuthorIds(authorIds);
						List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id))
								.map(optionalAuthor -> {
									if (optionalAuthor.isEmpty()) return "Unknown Author";
									return optionalAuthor.get().getName();
								}).collect(Collectors.toList());
						book.setAuthorNames(authorNames);
					}

					// Persist using Repository
					bookRepository.save(book);

				} catch (JSONException e) {
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This is necessary to have the Spring Boot app use the Astra secure bundle
	 * to connect to the database
	 */
	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}
}
