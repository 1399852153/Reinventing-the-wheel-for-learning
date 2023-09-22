package softreference;

import java.lang.ref.WeakReference;

public class UserWithSoftReference<User> extends WeakReference<User> {

    public UserWithSoftReference(User referent) {
        super(referent);
    }
}
