package com.hasura.todo.Todo.ui.todos

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.apollographql.apollo.fetcher.ResponseFetcher
import com.hasura.todo.AddTodoMutation
import com.hasura.todo.GetMyTodosQuery
import com.hasura.todo.Todo.R
import com.hasura.todo.Todo.network.Network
import com.hasura.todo.ToggleTodoMutation
import kotlinx.android.synthetic.main.task_todos.*
import kotlinx.android.synthetic.main.task_todos.view.*
import java.util.*

private const val COMPLETE_STATUS = "status"

class TaskFragment : Fragment(), TaskAdapter.TaskItemClickListener {

    private lateinit var getMyTodosQuery: GetMyTodosQuery
    private lateinit var addTodoMutation: AddTodoMutation
    private lateinit var toggleTodoMutation: ToggleTodoMutation

    private var completeStatus: String? = null

    companion object {
        const val ALL = "ALL"
        const val ACTIVE = "ACTIVE"
        const val COMPLETED = "COMPLETED"
        private var fragmentListener: FragmentListener? = null

        var listItems: MutableList<GetMyTodosQuery.Todo> = mutableListOf()

        @JvmStatic
        fun newInstance(completeStatus: String): TaskFragment {
            return TaskFragment().apply {
                arguments = Bundle().apply {
                    putString(COMPLETE_STATUS, completeStatus)
                }
            }
        }
    }

    interface FragmentListener {
        fun notifyDataSetChanged()
    }

    private var filteredListItems = listItems

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            completeStatus = it.getString(COMPLETE_STATUS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.task_todos, container, false)
        val removeAllCompleted: Button = root.removeAllCompleted
        removeAllCompleted.setOnClickListener { removeAllCompleted() }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        taskRecyclerView.layoutManager = LinearLayoutManager(activity)
        updateTabs()
        getMyTodoQueryCloud()
    }

    fun refreshData() {
        updateTabs()
    }

    private fun updateTabs() {
        filteredListItems = listItems
        when (completeStatus) {
            ALL -> getFilteredData(filteredListItems)
            ACTIVE -> getFilteredData(filteredListItems.filter { task -> !task.is_completed } as MutableList<GetMyTodosQuery.Todo>)
            COMPLETED -> getFilteredData(filteredListItems.filter { task -> task.is_completed } as MutableList<GetMyTodosQuery.Todo>)
        }
        taskRecyclerView.adapter?.notifyDataSetChanged()
    }

    private fun getFilteredData(list: MutableList<GetMyTodosQuery.Todo>) {
        if (list.isNotEmpty()) {
            emptyMessageTextView.visibility = View.INVISIBLE
            taskRecyclerView.visibility = View.VISIBLE
            val taskAdapter = TaskAdapter(list, this@TaskFragment)
            taskRecyclerView.swapAdapter(taskAdapter, true)
            if (completeStatus == COMPLETED) {
                removeAllCompleted.visibility = View.VISIBLE
            }
        } else {
            removeAllCompleted.visibility = View.INVISIBLE
            emptyMessageTextView.visibility = View.VISIBLE
            when (completeStatus) {
                ACTIVE -> emptyMessageTextView.text = "No Active Tasks!"
                COMPLETED -> emptyMessageTextView.text = "No Completed Tasks Yet!"
            }
            taskRecyclerView.visibility = View.INVISIBLE
        }
    }

    private fun getMyTodoQueryCloud() {
        getMyTodosQuery = GetMyTodosQuery()
        Network.apolloClient.query(getMyTodosQuery)
            ?.enqueue(object : ApolloCall.Callback<GetMyTodosQuery.Data>() {
                override fun onFailure(e: ApolloException) {
                    Log.d("getMyTodosQuery", e.toString())
                }

                override fun onResponse(response: Response<GetMyTodosQuery.Data>) {
                    Log.d("getMyTodosQuery", response.data().toString())
                    response.data()?.todos?.toMutableList()?.let {
                        listItems = it
                        activity?.runOnUiThread { updateTabs() }
                    }
                }
            })
    }

    private fun addTodoMutationCloud(title: String) {
        // init query
        addTodoMutation = AddTodoMutation(todo = title, isPublic = false)

        Network.apolloClient.mutate(addTodoMutation)
            .enqueue(object : ApolloCall.Callback<AddTodoMutation.Data>() {
                override fun onFailure(e: ApolloException) {
                    Log.d("addTodoMutation", e.toString())
                }

                override fun onResponse(response: Response<AddTodoMutation.Data>) {
                    Log.d("addTodoMutation", response.data().toString())

                    // create Data class instance from response
                    val addedTodo = response.data()?.insert_todos?.returning?.get(0) ?: return
                    val todo = GetMyTodosQuery.Todo(
                        addedTodo.__typename,
                        addedTodo.id,
                        addedTodo.title,
                        addedTodo.created_at,
                        addedTodo.is_completed
                    )
                    // cache immediately
                    Network.apolloClient.apolloStore.write(
                        GetMyTodosQuery(), GetMyTodosQuery.Data(
                            listOf(todo)
                        )
                    )
                    getMyTodosQueryLocal()
                }
            })
    }

    private fun getMyTodosQueryLocal() {
        getMyTodosQuery = GetMyTodosQuery()
        Network.apolloClient.query(getMyTodosQuery)
            .responseFetcher(ApolloResponseFetchers.CACHE_FIRST) // local first
            .enqueue(object : ApolloCall.Callback<GetMyTodosQuery.Data>() {
                override fun onFailure(e: ApolloException) {
                    Log.d("Todo", e.toString())
                }

                override fun onResponse(response: Response<GetMyTodosQuery.Data>) {
                    Log.d("getMyTodosQueryLocal", "${response.data()?.todos}")
                    response.data()?.todos?.toMutableList()?.let {
                        listItems = it
                        requireActivity().runOnUiThread { updateTabs() }
                    }
                }
            })
    }

    private fun toggleTodoMutationCloud(todoId: Int, completeFlag: Boolean) {
        toggleTodoMutation = ToggleTodoMutation(id = todoId, isCompleted = completeFlag)
        getMyTodosQuery = GetMyTodosQuery()

        val index = listItems.indexOfFirst { it.id == todoId }
        val todos = listItems[index]
        val todo = GetMyTodosQuery.Todo(
            todos.__typename,
            todos.id,
            todos.title,
            todos.created_at,
            todos.is_completed
        )

        val updatedList = listItems.apply {
            this[index] = todo
        }

        // optimistic cache
        Network.apolloClient.apolloStore.writeOptimisticUpdatesAndPublish(
            GetMyTodosQuery(), GetMyTodosQuery.Data(
                mutableListOf(todo)
            ), UUID.randomUUID()
        ).execute()
        getMyTodosQueryLocal()

        // Apollo runs query on background thread
        Network.apolloClient.mutate(toggleTodoMutation)?.enqueue(object : ApolloCall.Callback<ToggleTodoMutation.Data>() {
            override fun onFailure(error: ApolloException) {
                Log.d("Todo", error.toString() )
            }
            override fun onResponse(response: Response<ToggleTodoMutation.Data>) {
                Network.apolloClient.apolloStore.write(toggleTodoMutation, response.data()!!)
                getMyTodosQueryLocal()
            }
        })
    }

    override fun updateTaskCompleteStatus(taskId: Int, completeFlag: Boolean) {
        toggleTodoMutationCloud(taskId, completeFlag)
    }

    fun addTodo(title: String) {
        addTodoMutationCloud(title)
    }

    override fun delete(taskId: Int) {
        // Todo : Method for deleting a task
    }

    private fun removeAllCompleted() {
        // Todo : Method for clearing all completed task at once
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        // check if parent Fragment implements listener
        if (parentFragment is FragmentListener) {
            fragmentListener = parentFragment as FragmentListener
        } else {
            throw RuntimeException("$parentFragment must implement FragmentListener")
        }

    }

    override fun onDetach() {
        super.onDetach()
        fragmentListener = null
    }
}