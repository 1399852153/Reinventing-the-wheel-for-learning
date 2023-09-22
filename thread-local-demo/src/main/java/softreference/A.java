package softreference;

public class A {

    private final UserWithSoftReference<User> user;

    public A(User user) {
        this.user = new UserWithSoftReference<>(user);
    }

    public User getUser() {
        return user.get();
    }
}
