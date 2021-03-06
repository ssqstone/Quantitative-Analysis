package ssq.stock.analyser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.ListIterator;

import ssq.stock.DateData;
import ssq.stock.Stock;
import ssq.stock.analyser.ReflectTreeBuilder.ValueType;

public class RangeAnalyzer extends Analyzer
{

    File outPre  = new File("横盘统计.txt");
    File outNow  = new File("牛市统计.txt");

    File outGrow = new File("大涨前夕.txt");
    File outDrop = new File("大跌前夕.txt");

    public static void main(String[] args) throws Exception
    {
        new RangeAnalyzer().run();
    }
    
    public RangeAnalyzer()
    {
        if (outNow.isFile())
        {
            outNow.delete();
        }

        if (outPre.isFile())
        {
            outPre.delete();
        }
    }
    
    public static class Recoder
    {
        LinkedList<DateData> dateOrder = new LinkedList<>();
        LinkedList<DateData> valOrder  = new LinkedList<>();
        int                  capacity  = 250;

        public void insert(DateData data)
        {
            float scaledVal = data.getScaledVal(ValueType.closing);
            boolean succ = false;
            for (ListIterator<DateData> i = valOrder.listIterator(); i.hasNext();)
            {
                DateData tmpData = i.next();

                if (tmpData.getScaledVal(ValueType.closing) < scaledVal)
                {
                    succ = true;
                    if (i.hasPrevious())
                    {
                        i.previous();
                        i.add(data);
                    }
                    else
                    {
                        valOrder.addFirst(data);
                    }
                    
                    break;
                }
            }
            
            if (!succ)
            {
                valOrder.addLast(data);
            }

            dateOrder.addFirst(data);
            
            if (dateOrder.size() > capacity)
            {
                valOrder.remove(dateOrder.getLast());
                dateOrder.removeLast();
            }
        }
    }
    
    @Override
    public void scan(Stock s)
    {
        try
        {
            if (s.history.size() == 0)
            {
                throw new Exception(s + " 空文件");
            }
            
            Recoder recoder = new Recoder();
            
            DateData maxInQueue = null;
            DateData minInQueue = null;
            
            for (int i = 0; i < s.history.size(); i++)
            {
                DateData thisDay = s.history.get(i);
                float scaledVal = thisDay.getScaledVal(ValueType.closing);
                
                recoder.insert(thisDay);
                
                if (minInQueue != null) //在上升序列
                {
                    if (recoder.valOrder.getFirst().getScaledVal(ValueType.closing) == scaledVal) //比昨天更高了
                    {
                        maxInQueue = thisDay;
                    }
                    else if (scaledVal < maxInQueue.getScaledVal(ValueType.closing) * 0.9) //跌破
                    {
                        BufferedWriter fout;
                        
                        if (minInQueue.date > 20141100)
                        {
                            fout = new BufferedWriter(new FileWriter(outNow, true));
                        }
                        else if (minInQueue.date < 20141100 && minInQueue.date > 20110800)
                        {
                            fout = new BufferedWriter(new FileWriter(outPre, true));
                        }
                        else
                        {
                            minInQueue = null; //结束上升序列
                            continue;
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append(s.getCodeString()).append(' ')
                        .append(minInQueue.date).append(' ').append(minInQueue.getVal(ValueType.closing)).append(' ').append(String.valueOf(minInQueue.getVal(ValueType.scale)).substring(0, Math.min(4, String.valueOf(minInQueue.getVal(ValueType.scale)).length()))).append(' ').append(minInQueue.getScaledVal(ValueType.closing)).append(' ')
                        .append(maxInQueue.date).append(' ').append(maxInQueue.getVal(ValueType.closing)).append(' ').append(String.valueOf(maxInQueue.getVal(ValueType.scale)).substring(0, Math.min(4, String.valueOf(maxInQueue.getVal(ValueType.scale)).length()))).append(' ').append(maxInQueue.getScaledVal(ValueType.closing)).append(' ')
                        .append(thisDay.date).append(' ').append(thisDay.getVal(ValueType.closing)).append(' ').append(String.valueOf(thisDay.getVal(ValueType.scale)).substring(0, Math.min(4, String.valueOf(thisDay.getVal(ValueType.scale)).length()))).append(' ').append(thisDay.getScaledVal(ValueType.closing)).append(' ')
                        .append((DateData.numberToDate(maxInQueue.date).getTime() - DateData.numberToDate(minInQueue.date).getTime()) / (1000 * 3600 * 24)).append(' ')
                        .append((DateData.numberToDate(thisDay.date).getTime() - DateData.numberToDate(maxInQueue.date).getTime()) / (1000 * 3600 * 24)).append(' ')
                        .append(maxInQueue.getScaledVal(ValueType.closing) / minInQueue.getScaledVal(ValueType.closing) - 1f).append(' ').append(1f - thisDay.getScaledVal(ValueType.closing) / maxInQueue.getScaledVal(ValueType.closing)).append(' ')
                        .append("\r\n");
                        
                        fout.write(sb.toString());
                        fout.close();

                        minInQueue = null; //结束上升序列
                    }
                }
                else
                {
                    if (recoder.valOrder.getFirst().getScaledVal(ValueType.closing) == scaledVal) //是250个交易日以来的新高
                    {
                        maxInQueue = thisDay;
                        minInQueue = thisDay; //开始上升序列
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
