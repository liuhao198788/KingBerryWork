package com.kingberry.liuhao;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import com.kingberry.liuhao.drag.DragLayer;

/**
 * Created by Administrator on 2017/7/17.
 */

public class HorizontalPageLayoutManager extends RecyclerView.LayoutManager {
    private static final String TAG = "HorizontalPageLayoutManager";
    private final Context context;
    int itemWidthUsed;
    int itemHeightUsed;
    int itemWidth = 0;
    int itemHeight = 0;
    //总的页数
    int pageSize = 0;
    private int rows = 0;
    private int columns = 0;
    private int onePageSize = 0;

    private int offsetX = 0; // x偏移量
    private int totalWidth = 0; //总宽度

    private int lineWidth=0;//网格线的宽度

    //保存所有的Item的上下左右的偏移量信息，为了回收
    private SparseArray<Rect> allItemFrames = new SparseArray<>();

    public HorizontalPageLayoutManager(int rows, int columns, int lineWidth,Context context) {
        this.rows = rows;
        this.columns = columns;
        this.onePageSize = rows * columns;
        this.context = context;
        this.lineWidth=lineWidth;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /**
     * @return true 设置可以横向滑动
     */
    @Override
    public boolean canScrollHorizontally() {
        return true;
    }

    /**
     * 横向滑动距离
     *
     * @param dx
     * @param recycler
     * @param state
     * @return
     */
    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        //Temporarily detach and scrap all currently attached child views.

        //分离
        detachAndScrapAttachedViews(recycler);
        int newX = offsetX + dx;
        int result = dx;
        if (newX > totalWidth) {
            result = totalWidth - offsetX;
        } else if (newX < 0) {
            result = 0 - offsetX;
        }
//      Log.e("*********","offsetX:"+offsetX +"--totalWidth:"+totalWidth+"--result:"+result+"--dx:"+dx);
        offsetX += result;
        offsetChildrenHorizontal(-result);//滑动
        // 先将不需要的Item进行回收，然后在从缓存中取出需要的Item
        recycleAndFillItems(recycler, state);
        return result;
    }

    //对子 VIew 布局
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        //注意，这句是要解决从app切换到其他竖屏的界面再切换回来以后，要保证焦点不变。
        //什么？你说为啥会变？因为真的会变啊，不信你看代码= =、
       // if (!shouldLayoutChildren()) return; //delete by liuhao 0718

//        Lg.v("onLayoutChildren() called with: " + "state = [" + state + "]");
        int itemCount = getItemCount();
        if (itemCount == 0) {
            removeAndRecycleAllViews(recycler);
            return;
        }
        //  Returns true if the RecyclerView is currently measuring the layout.
        if (state.isPreLayout()) {
            return;
        }
//        Lg.i("onLayoutChildren() called with: " + "state = [" + state + "]");
        //获取每个Item的平均宽高
        itemWidth = (getUsableWidth()-(columns+1)*lineWidth)/ columns;
        itemHeight = (getUsableHeight()-(rows+1)*lineWidth)/ rows;

        //计算宽高已经使用的量，主要用于后期测量
        itemWidthUsed = (columns - 1) * itemWidth;
        itemHeightUsed = (rows - 1) * itemHeight;

        //计算总的页数
        pageSize = getPageSize(itemCount);

        //计算可以横向滚动的最大值
        totalWidth = (pageSize - 1) * getWidth();

        //分离view
        detachAndScrapAttachedViews(recycler);

        if (itemCount > 0) {
//            View view = recycler.getViewForPosition(0);
//            addView(view);
//            measureChildWithMargins(view, itemWidthUsed, itemHeightUsed);
//            int width = getDecoratedMeasuredWidth(view);
//            int height = getDecoratedMeasuredHeight(view);
//            removeAndRecycleAllViews(recycler);
            int width = itemWidth;
            int height = itemHeight;

           // int x=0 ,y=0,hLineHeight=lineWidth;

            for (int p = 0; p < pageSize; p++) {
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < columns; c++) {
                        int index = p * onePageSize + r * columns + c;
                        if (index == itemCount) {
                            //跳出多重循环
                            c = columns;
                            r = rows;
                            p = pageSize;
                            break;
                        }

                        //获取对应位置的 view
                        //测量item

                       //记录显示范围
                        Rect rect = allItemFrames.get(index);
                        if (rect == null) {
                            rect = new Rect();
                        }
                       int x = p * getUsableWidth() + c * itemWidth+lineWidth*(c+1);
                       int y = r * itemHeight+lineWidth*(r+1);

                        rect.set(x, y, width + x, height + y);
                        // 将当前的Item的Rect边界数据保存
                        allItemFrames.put(index, rect);
                    }
                }
                //每一页循环以后就回收一页的View用于下一页的使用
                removeAndRecycleAllViews(recycler);
            }
        }
        recycleAndFillItems(recycler, state);
    }

    public int getPageSize(int itemCount) {
        if (onePageSize == 0) {
            Exception e =new Exception("行和列不能为空");
            e.printStackTrace();
            return 0;
        }
        return itemCount / onePageSize + (itemCount % onePageSize == 0 ? 0 : 1);
    }

    protected boolean shouldLayoutChildren() {
        /* >benny: [16-01-13 20:20] 宽度必须大于高度, 主要是考虑解锁时瞬间的竖屏状态导致的焦点偏移 */
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && getWidth() > getHeight();
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        offsetX = 0;
    }

    /**
     * 回收不需要的Item，并且将需要显示的Item从缓存中取出
     */
    private void recycleAndFillItems(RecyclerView.Recycler recycler, RecyclerView.State state) {
        //  Returns true if the RecyclerView is currently measuring the layout.
        if (state.isPreLayout()) {
            return;
        }
        // 当前scroll offset状态下的显示区域
        Rect displayRect = new Rect(getPaddingLeft() + offsetX,
                getPaddingTop(),
                getWidth() - getPaddingLeft() - getPaddingRight() + offsetX,
                getHeight() - getPaddingTop() - getPaddingBottom());
        /**
         * 将滑出屏幕的Items回收到Recycle缓存中
         */
        Rect childRect = new Rect();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            childRect.left = getDecoratedLeft(child);
            childRect.top = getDecoratedTop(child);
            childRect.right = getDecoratedRight(child);
            childRect.bottom = getDecoratedBottom(child);
            if (!Rect.intersects(displayRect, childRect)) {//不相交，remote
                removeAndRecycleView(child, recycler);
            }
        }
        /**
         * 重新显示需要出现在屏幕的子View
         */
        int itemCount = getItemCount();
        for (int i = 0; i < itemCount; i++) {
            //判断矩形是否相交
            if (Rect.intersects(displayRect, allItemFrames.get(i))) {
                View view = recycler.getViewForPosition(i);
                //DraggableLayout view= (DraggableLayout) view1.findViewById(R.id.layout);
                addView(view);
                measureChildWithMargins(view, itemWidthUsed, itemHeightUsed);
                Rect rect = allItemFrames.get(i);
                layoutDecorated(view, rect.left - offsetX, rect.top, rect.right - offsetX, rect.bottom);

                if(mDragLayer != null){
                    mDragLayer.loadChildView(view);
                }
            }
        }
    }

    private int getUsableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getUsableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private DragLayer mDragLayer = null;
    public void setDragLayer(DragLayer layer){
        this.mDragLayer = layer;
    }
}
