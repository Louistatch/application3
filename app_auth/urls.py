from django.urls import path
from .views import login_blog,register,logout_blog
urlpatterns= [
path('login',login_blog,name='login_blog'),
path('register',register,name='register'),
path('logout',logout_blog,name='logout'),

]