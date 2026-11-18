package com.example.cse226new

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String
)

@Dao
interface ContactDao {

    @Query("""
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchContacts(query: String): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<Contact>)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCount(): Int
}

@Database(entities = [Contact::class], version = 1)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContactViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val dao: ContactDao
) : ViewModel() {

    companion object {
        const val SEARCH_QUERY = "search_query"
    }

    // Preserved across process death using SavedStateHandle
    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(
        SEARCH_QUERY, ""
    )

    // Filtered Room query updated dynamically when searchQuery changes
    val contacts: StateFlow<List<Contact>> = searchQuery
        .flatMapLatest { query ->
            dao.searchContacts(query)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun updateSearchQuery(query: String) {
        savedStateHandle[SEARCH_QUERY] = query
    }
}

class ContactViewModelFactory(
    private val dao: ContactDao
) : AbstractSavedStateViewModelFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return ContactViewModel(handle, dao) as T
    }
}

class MigrationActivity : ComponentActivity() {

    private lateinit var database: ContactDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = Room.databaseBuilder(
            applicationContext,
            ContactDatabase::class.java,
            "contact_database"
        ).build()

        // Seed initial sample contacts if database is empty
        lifecycleScope.launch {
            if (database.contactDao().getContactCount() == 0) {
                database.contactDao().insertContacts(
                    listOf(
                        Contact(name = "Aditya", phone = "123-456-7890"),
                        Contact(name = "Ravi", phone = "987-654-3210"),
                        Contact(name = "Charles", phone = "555-012-3456"),
                        Contact(name = "David", phone = "444-555-6666"),
                        Contact(name = "Wilson", phone = "777-888-9999")
                    )
                )
            }
        }

        val factory = ContactViewModelFactory(
            database.contactDao()
        )

        val viewModel = ViewModelProvider(
            this,
            factory
        )[ContactViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContactScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ContactScreen(viewModel: ContactViewModel) {

    val query by viewModel.searchQuery.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search Contacts") },
            placeholder = { Text("Type name to search...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Text("Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isEmpty()) "No contacts found" else "No matching contacts for '$query'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = contact.phone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
