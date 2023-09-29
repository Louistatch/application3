from django.shortcuts import render,redirect
from django.views.generic.edit import UpdateView,CreateView,DeleteView
from django.contrib.auth.mixins import LoginRequiredMixin
from blog.models import Article
from blog.forms import ArticleForm
from django.urls import reverse_lazy

# Create your views here.
def dashboard(request):
    return render(request, "db.html")

def user_articles(request):
    if not request.user.is_authenticated:
        return redirect('index')
    list_articles = Article.objects.filter(user=request.user)
    return render(request, 'my-articles.html',{'list_articles':list_articles})



class AddArticle(LoginRequiredMixin,CreateView):
    model = Article
    form_class = ArticleForm
    template_name ="add-article.html"

    def form_valid(self,form):
        form.instance.user=self.request.user
        return super().form_valid(form)
     
class UpdateArticle(LoginRequiredMixin,UpdateView):
    model = Article
    form_class = ArticleForm
    template_name = 'app_admin/article_form.html'

class DeleteArticle(DeleteView):
    model = Article
    success_url = "/my-admin/my-articles"
