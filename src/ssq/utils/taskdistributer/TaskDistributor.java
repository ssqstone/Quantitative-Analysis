package ssq.utils.taskdistributer;

import java.util.LinkedList;
import java.util.Random;

/**
 * 指派任务列表给线程的分发器
 *
 */
public class TaskDistributor
{
    final private TaskList taskList;
    private int            running      = 0;
    private int            finished     = 0;
    private int            aborted      = 0;
    private int            capacity     = -1;
    int                    toBeRunIndex = 0;                 //指针之前的都是分配过的任务(可能运行失败过并被要求重复运行)
                                                              
    LinkedList<Thread>     threads      = new LinkedList<>();
    
    public static void main(String[] args)
    {
        int numOfThreads = 5;
        final int cnt = 55;
        
        TaskList taskList = new TaskList();
        final TaskDistributor distributor = new TaskDistributor(taskList, numOfThreads)
        {
            @Override
            public synchronized Task getNext(int lastFinished)
            {
                System.out.println('\t' + getProgressString());

                return super.getNext(lastFinished);
            }
            
            @Override
            public void informException(Exception e)
            {
                super.informException(e);
            }
        };
        
        for (int i = 0; i < cnt; i++)
        {
            taskList.add(new Task(i)
            {
                @Override
                public void execute()
                {
                    super.execute();
                    int tmp = new Random().nextInt(700);

                    if (tmp > 600)
                    {
                        distributor.abort(getTaskId());
                    }
                    else if (tmp > 500)
                    {
                        distributor.redoLater(getTaskId());
                    }
                    else
                    {
                        try
                        {
                            Thread.sleep(tmp);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        distributor.schedule();
        //        distributor.waitTasksDone();
        distributor.schedule();
        //        distributor.waitTasksDone();
        distributor.schedule();
        //        distributor.waitTasksDone();
    }

    public String getProgressString()
    {
        StringBuilder sb = new StringBuilder();
        String progress = String.valueOf(getProgress());
        progress = progress.substring(0, Math.min(5, progress.length()));
        
        sb.append("running: ").append(running).append(" , finished: ").append(finished).append(", aborted: ").append(aborted).append(", progress: ").append(capacity > 0 ? progress : "-1").append('%');

        return sb.toString();
    }

    public float getProgress()
    {
        return 100.0f * (finished + aborted) / taskList.size();
    }

    public TaskDistributor(TaskList taskList, int capacity)
    {
        this.taskList = taskList;
        this.capacity = capacity;
    }
    
    Runnable runnable = new Runnable()
    {
        @Override
        public void run()
        {
            for (Task task = getNext(-1); task != null; task = getNext(task.getTaskId()))
            {
                task.execute();
            }
        }
    };
    
    public void schedule()
    {
        synchronized (this)
        {
            resetStatus();
            threads.clear();

            for (int i = 0; i < capacity; ++i)
            {
                Thread thisThread = new Thread(runnable);
                thisThread.start();

                threads.add(thisThread);
            }
        }
    }
    
    public void resetStatus()
    {
        running = 0;
        finished = 0;
        aborted = 0;
        toBeRunIndex = 0;
        
        for (Task task : taskList)
        {
            task.resetStatus();
        }
    }
    
    public Task getNext(int lastFinished)
    {
        synchronized (this)
        {
            finish(lastFinished);
            
            return getNext();
        }
    }
    
    public Task getNext()
    {
        synchronized (this)
        {
            for (int i = toBeRunIndex; i < taskList.size(); i++)
            {
                Task task = taskList.get(i);
                if (task.getStatus() == Task.READY)
                {
                    running++;
                    toBeRunIndex = i + 1;
                    task.onStart();
                    return task;
                }
            }
            
            toBeRunIndex = taskList.size();
            return null;
        }
    }
    
    public void finish(int lastFinished)
    {
        synchronized (this)
        {
            if (lastFinished >= 0)
            {
                Task task = taskList.get(lastFinished);
                
                if (task.getStatus() == Task.RUNNING)
                {
                    finished++;
                    running--;
                    task.onFinished();
                }
            }
        }
    }
    
    /**
     *
     * @param taskid
     * @return
     */
     public void redoLater(int taskid)
     {
         synchronized (this)
         {
             Task task = taskList.get(taskid);
            
            if (task.getStatus() == Task.RUNNING)
             {
                 running--;
                 task.onRedoLater();
                 toBeRunIndex = Math.min(toBeRunIndex, taskid);
             }
         }
     }

     /**
      * 就绪或运行状态的程序可以被abort, 已完成的不可以.
      *
      * @param taskid
      */
     public void abort(int taskid)
     {
         synchronized (this)
         {
             Task task = taskList.get(taskid);
             int status = task.getStatus();

             if (status != Task.FINISHED)
             {
                 aborted++;

                 if (status == Task.RUNNING)
                 {
                     running--;
                 }

                 task.onAborted();
             }
         }
     }

     public void informException(Exception e)
     {
     }

     public void waitTasksDone()
     {
         for (Thread thread : threads)
         {
             try
             {
                 thread.join();
             }
             catch (InterruptedException e)
             {
                 e.printStackTrace();
             }
         }
     }
}
