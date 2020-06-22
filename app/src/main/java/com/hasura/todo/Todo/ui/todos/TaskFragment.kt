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
import com.hasura.todo.GetMyTodosQuery
import com.hasura.todo.Todo.R
import com.hasura.todo.Todo.network.Network
import kotlinx.android.synthetic.main.task_todos.*
import kotlinx.android.synthetic.main.task_todos.view.*

private const val COMPLETE_STATUS = "status"

class TaskFragment : Fragment(), TaskAdapter.TaskItemClickListener {

    private lateinit var getMyTodosQuery: GetMyTodosQuery

    private var completeStatus: String? = null

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
                    Log.d("Todo", e.toString())
                }

                override fun onResponse(response: Response<GetMyTodosQuery.Data>) {
                    Log.d("Todo", response.data().toString())
                    response.data()?.todos?.toMutableList()?.let {
                        listItems = it
                        activity?.runOnUiThread { updateTabs() }
                    }
                }
            })
    }

    override fun updateTaskCompleteStatus(taskId: Int, completeFlag: Boolean) {
        // Todo : Method for updating the complete status for the task
    }

    fun addTodo(title: String) {
        // Todo : Add method to update todos
    }

    override fun delete(taskId: Int) {
        // Todo : Method for deleting a task
    }

    private fun removeAllCompleted() {
        // Todo : Method for clearing all completed task at once
    }

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