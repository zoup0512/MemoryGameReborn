package com.activeandroid.widget;

import android.content.Context;
import android.widget.ArrayAdapter;
import com.activeandroid.Model;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ModelAdapter<T extends Model> extends ArrayAdapter<T> {
    public ModelAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public ModelAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);
    }

    public ModelAdapter(Context context, int textViewResourceId, List<T> objects) {
        super(context, textViewResourceId, objects);
    }

    public ModelAdapter(Context context, int resource, int textViewResourceId, List<T> objects) {
        super(context, resource, textViewResourceId, objects);
    }

    public void setData(Collection<? extends T> collection) {
        clear();
        if (collection != null) {
            Iterator it = collection.iterator();
            while (it.hasNext()) {
                add((T) it.next());
            }
        }
    }

    public long getItemId(int position) {
        Model item = (Model) getItem(position);
        if (item != null) {
            return item.getId().longValue();
        }
        return -1;
    }
}
